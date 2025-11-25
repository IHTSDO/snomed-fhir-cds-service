package org.snomed.cdsservice.service.medication;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSCoding;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSReference;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.snomed.cdsservice.service.*;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.snomed.cdsservice.service.tsclient.ConceptParameters;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.service.tsclient.SnomedConceptNormalForm;
import org.snomed.cdsservice.util.SnomedValueSetUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MedicationOrderSelectCDSService extends CDSService {

	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private FhirContext fhirContext;

	@Autowired
	private MedicationConditionRuleLoaderService ruleLoaderService;

	@Autowired
	private MedicationCombinationRuleLoaderService medicationRuleLoaderService;

	@Autowired
	private SnomedMedicationDefinedDailyDoseService definedDailyDoseService;

	@Autowired
	private FHIRTerminologyServerClient tsClient;

	private List<CDSTrigger> medicationOrderSelectTriggers;

	private List<CDSTrigger> drugDrugInteractionTriggers;

	public MedicationOrderSelectCDSService() {
		super("medication-order-select");
		setPrefetch(Map.of(
				"conditions", "Condition?patient={{context.patientId}}&category=problem-list-item&status=active",
				"draftMedicationRequests", "MedicationRequest?patient={{context.patientId}}&status=draft",
				"allergies", "AllergyIntolerance?patient={{context.patientId}}&clinical-status=active"
		));
	}

	@PostConstruct
	public void init() throws ServiceException {
		medicationOrderSelectTriggers = ruleLoaderService.loadTriggers();
		drugDrugInteractionTriggers = medicationRuleLoaderService.loadTriggers();
	}

	@Override
	public List<CDSCard> call(CDSRequest cdsRequest) {
		Map<String, String> prefetch = cdsRequest.getPrefetchStrings();
		if (prefetch == null || prefetch.get("patient") == null || prefetch.get("conditions") == null || prefetch.get("draftMedicationRequests") == null) {
			throw new ResponseStatusException(412, "Request does not include required prefetch information: patient, diagnosis and medications.", null);
		}

		IParser parser = fhirContext.newJsonParser();
		List<Condition> activeDiagnoses = getPrefetchResourcesFromBundle(prefetch, "conditions", Condition.class, parser);
		List<MedicationRequest> medicationRequests = getPrefetchResourcesFromBundle(prefetch, "draftMedicationRequests", MedicationRequest.class, parser);
		
		// Read allergies from prefetch (if available)
		List<AllergyIntolerance> activeAllergies = new ArrayList<>();
		if (prefetch.get("allergies") != null) {
			activeAllergies = getPrefetchResourcesFromBundle(prefetch, "allergies", AllergyIntolerance.class, parser);
		}

		Set<Coding> activeDiagnosesCodings = getCodings(activeDiagnoses.stream().map(Condition::getCode));
		Set<Coding> draftMedicationOrderCodings = getCodings(medicationRequests.stream().map(MedicationRequest::getMedicationCodeableConcept));
		Set<Coding> allergySubstanceCodings = getAllergyCodings(activeAllergies);
		
		// Log allergies found for debugging
		if (!activeAllergies.isEmpty()) {
			logger.debug("Processing {} allergy intolerance(s)", activeAllergies.size());
			logger.debug("Extracted {} allergen substance code(s)", allergySubstanceCodings.size());
			allergySubstanceCodings.forEach(coding ->
				logger.debug("Allergen substance: {} (Code: {})",
					coding.getDisplay() != null ? coding.getDisplay() : coding.getCode(), 
					coding.getCode())
			);
		}

		List<CDSCard> cards = new ArrayList<>();
		for (CDSTrigger trigger : medicationOrderSelectTriggers) {
			CDSCard card = trigger.createRelevantCard(activeDiagnosesCodings, draftMedicationOrderCodings);
			if (card != null) {
				addCodesFromOtherCodingSystemsForDraftMedications(card.getReferenceMedications(), medicationRequests);
				addCodesFromOtherCodingSystemsForConditions(card.getReferenceConditions(), activeDiagnoses);
				cards.add(card);
			}
		}

		drugDrugInteractionTriggers.forEach(trigger -> {
			CDSCard card = trigger.createRelevantCard(draftMedicationOrderCodings, draftMedicationOrderCodings);
			if (card != null) {
				addCodesFromOtherCodingSystemsForDraftMedications(card.getReferenceMedications(), medicationRequests);
				cards.add(card);
			}
		});

		cards.addAll(definedDailyDoseService.checkMedications(medicationRequests));
		
		// Check for allergy-medication conflicts
		if (!activeAllergies.isEmpty() && !medicationRequests.isEmpty()) {
			cards.addAll(checkAllergyMedicationConflicts(allergySubstanceCodings, medicationRequests));
		}

		return cards;
	}

	private void addCodesFromOtherCodingSystemsForConditions(List<CDSReference> referenceConditions, List<Condition> activeDiagnoses) {
		referenceConditions.forEach(referenceCondition-> {
					CDSCoding cdsCoding = referenceCondition.getCoding().get(0);
					Optional<Condition> optionalCondition = activeDiagnoses.stream().filter(condition -> {
						List<Coding> codingList = condition.getCode().getCoding();
						Optional<Coding> optionalCoding = codingList.stream().filter(coding -> coding.getCode().equals(cdsCoding.getCode()) && coding.getSystem().equals(cdsCoding.getSystem())).findFirst();
						return optionalCoding.isPresent();
					}).findFirst();
					optionalCondition.ifPresent(getCDSReferenceConditionConsumer(referenceCondition));
				}
		);
	}

	private void addCodesFromOtherCodingSystemsForDraftMedications(List<CDSReference> referenceMedications, List<MedicationRequest> draftMedicationOrders) {
		referenceMedications.forEach(referenceMedication -> {
			CDSCoding cdsCoding = referenceMedication.getCoding().get(0);
			Optional<MedicationRequest> optionalMedicationRequest = draftMedicationOrders.stream().filter(medicationRequest -> {
				List<Coding> codingList = medicationRequest.getMedicationCodeableConcept().getCoding();
				Optional<Coding> optionalCoding = codingList.stream().filter(coding -> coding.getCode().equals(cdsCoding.getCode()) && coding.getSystem().equals(cdsCoding.getSystem())).findFirst();
				return optionalCoding.isPresent();
			}).findFirst();
			optionalMedicationRequest.ifPresent(getCDSReferenceMedicationReqquestConsumer(referenceMedication));
		});

	}

	@NotNull
	private Consumer<Condition> getCDSReferenceConditionConsumer(CDSReference reference) {
		return condition -> reference.setCoding(condition.getCode().getCoding().stream().map(getCodingCDSCodingFunction()).collect(Collectors.toList()));
	}

	@NotNull
	private Consumer<MedicationRequest> getCDSReferenceMedicationReqquestConsumer(CDSReference reference) {
		return medicationRequest -> reference.setCoding(medicationRequest.getMedicationCodeableConcept().getCoding().stream().map(getCodingCDSCodingFunction()).collect(Collectors.toList()));
	}

	@NotNull
	private Function<Coding, CDSCoding> getCodingCDSCodingFunction() {
		return coding -> new CDSCoding(coding.getSystem(), coding.getCode(), coding.getDisplay());
	}

	@NotNull
	private static Set<Coding> getCodings(Stream<CodeableConcept> codeableConceptStream) {
		return codeableConceptStream
				.map(CodeableConcept::getCoding)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
	}

	private <T extends Resource> List<T> getPrefetchResourcesFromBundle(Map<String, String> prefetch, String name, Class<T> theResourceType, IParser parser) {
		String value = prefetch.get(name);
		Bundle bundle = parser.parseResource(Bundle.class, value);
		List<T> resources = new ArrayList<>();
		for (Bundle.BundleEntryComponent component : bundle.getEntry()) {
			Resource resource = component.getResource();
			if (resource != null && resource.getResourceType().name().equals(theResourceType.getSimpleName())) {
				resources.add(theResourceType.cast(resource));
			}
		}
		return resources;
	}

	/**
	 * Extracts SNOMED substance codes from AllergyIntolerance resources.
	 * Handles two types of allergy codes:
	 * 1. Direct substance/ingredient codes (e.g., 387506000 |Atenolol|) - uses code directly
	 * 2. Propensity/finding codes (e.g., 293965006 |Allergy to atenolol|) - extracts causative agent via ECL
	 * 
	 * Strategy:
	 * - Try to resolve causative agent from code.coding first
	 * - If successful, use those substances (skip reaction.substance to avoid duplicates)
	 * - If not successful, fall back to code itself or reaction.substance
	 */
	private Set<Coding> getAllergyCodings(List<AllergyIntolerance> allergies) {
		Set<Coding> codings = new HashSet<>();
		
		for (AllergyIntolerance allergy : allergies) {
			boolean substancesExtracted = false;
			
			// First, try to get substances from the main allergy code
			if (allergy.hasCode() && allergy.getCode().hasCoding()) {
				for (Coding coding : allergy.getCode().getCoding()) {
					// Check if this is a SNOMED code
					if (coding.getSystem() != null && coding.getSystem().contains("snomed.info/sct")) {
						// Try to get causative agents (for propensity codes like "Allergy to X")
						Set<Coding> causativeAgents = getCausativeAgentsForAllergy(coding);
						if (!causativeAgents.isEmpty()) {
							// Successfully resolved causative agents - use them
							codings.addAll(causativeAgents);
							substancesExtracted = true;
						} else {
							// TO DO Add subsumption check to test if code is a substance
							// {{url}}/CodeSystem/$subsumes?system=http://snomed.info/sct&codeA=307355007&codeB=118940003

							// No causative agents found - code might be a direct substance
							// Check if it looks like a substance (not a propensity/finding)
							// For now, add it - reaction.substance will be skipped if we found substances
//							codings.add(coding);
//							substancesExtracted = true;
						}
					}
				}
			}
			
			// Only use reaction.substance as fallback if we didn't extract substances from code
			if (!substancesExtracted && allergy.hasReaction()) {
				for (AllergyIntolerance.AllergyIntoleranceReactionComponent reaction : allergy.getReaction()) {
					if (reaction.hasSubstance()) {
						CodeableConcept substance = reaction.getSubstance();
						if (substance != null && substance.hasCoding()) {
							codings.addAll(substance.getCoding());
						}
					}
				}
			}
		}
		
		return codings;
	}

	/**
	 * Checks for conflicts between patient allergies and medication ingredients.
	 * For each medication, extracts active ingredients and compares with allergy substances.
	 */
	private List<CDSCard> checkAllergyMedicationConflicts(Set<Coding> allergyCodings, List<MedicationRequest> medicationRequests) {
		List<CDSCard> allergyCards = new ArrayList<>();
		
		for (MedicationRequest medicationRequest : medicationRequests) {
			// Get SNOMED medication code
			List<Coding> codingList = medicationRequest.getMedicationCodeableConcept().getCoding();
			Optional<Coding> snomedMedication = codingList.stream()
					.filter(coding -> "http://snomed.info/sct".equals(coding.getSystem()))
					.findFirst();
			
			if (snomedMedication.isEmpty()) {
				continue;
			}
			
			String medicationCode = snomedMedication.get().getCode();
			String medicationDisplay = snomedMedication.get().getDisplay();
			
			try {
				// Get medication concept details from terminology server
				logger.debug("Looking up medication concept: {}", medicationCode);
				var conceptParameters = tsClient.lookup("http://snomed.info/sct", medicationCode);
				
				if (conceptParameters == null || conceptParameters.getNormalForm() == null) {
					logger.debug("No normal form found for medication {}, skipping allergy check", medicationCode);
					continue;
				}
				
				// Extract active ingredient substances from the medication
				Set<String> medicationSubstances = extractMedicationSubstances(conceptParameters.getNormalForm());
				
				if (medicationSubstances.isEmpty()) {
					logger.debug("No substances found in medication {}, skipping allergy check", medicationCode);
					continue;
				}
				
				logger.debug("Medication {} contains {} substance(s)", medicationDisplay, medicationSubstances.size());
				
				// Check each medication substance against allergies
				for (String substanceCode : medicationSubstances) {
					for (Coding allergyCoding : allergyCodings) {
						// Check if allergy substance matches or subsumes medication ingredient
						if (isAllergyMatch(allergyCoding.getCode(), substanceCode)) {
							logger.info("ALLERGY ALERT: Patient allergic to {} ({}), found in medication {} ({})",
									allergyCoding.getDisplay() != null ? allergyCoding.getDisplay() : allergyCoding.getCode(), 
									allergyCoding.getCode(),
									medicationDisplay, medicationCode);
							
							// Create allergy alert card
							CDSCard allergyCard = createAllergyAlertCard(
									allergyCoding, 
									snomedMedication.get(),
									medicationDisplay,
									substanceCode
							);
							allergyCards.add(allergyCard);
						}
					}
				}
				
			} catch (Exception e) {
				logger.warn("Error checking allergies for medication {}: {}", medicationCode, e.getMessage());
			}
		}
		
		return allergyCards;
	}

	/**
	 * Extracts active ingredient substance codes from a medication's normal form.
	 * Uses attribute 732943007 |Has basis of strength substance|
	 */
	private Set<String> extractMedicationSubstances(SnomedConceptNormalForm normalForm) {
		Set<String> substances = new HashSet<>();
		
		// Substances are in attribute groups
		for (Map<String, String> attributeGroup : normalForm.getAttributeGroups()) {
			String substance = attributeGroup.get("732943007"); // Has basis of strength substance
			if (substance != null) {
				substances.add(substance);
				logger.debug("Found substance: {}", substance);
			}
		}
		
		return substances;
	}

	/**
	 * Checks if an allergy substance matches or subsumes a medication ingredient.
	 * Uses SNOMED subsumption: allergy is dangerous if it equals or is parent of the ingredient.
	 * 
	 * Examples:
	 * - Allergy to Penicillin (372806008) matches Amoxicillin (372687004)
	 * - Allergy to Beta-blocker matches Atenolol
	 * - Allergy to Atenolol (387506000) matches Atenolol (387506000) - direct match
	 */
	private boolean isAllergyMatch(String allergySubstanceCode, String medicationSubstanceCode) {
		// 1. Direct match - allergy substance equals medication ingredient
		if (allergySubstanceCode.equals(medicationSubstanceCode)) {
			logger.debug("Direct match: allergy {} equals medication ingredient {}", 
					allergySubstanceCode, medicationSubstanceCode);
			return true;
		}
		
		// 2. Subsumption check - check if medication ingredient is a descendant of allergy substance
		// ECL: << allergySubstanceCode expands to all descendants
		try {
			String ecl = String.format("<< %s", allergySubstanceCode);
			String valueSetURI = SnomedValueSetUtil.getSnomedECLValueSetURI(ecl);
			
			logger.debug("Checking subsumption: is {} subsumed by {}?", 
					medicationSubstanceCode, allergySubstanceCode);
			
			// Expand the allergy substance to include all descendants
			Collection<Coding> descendants = tsClient.expandValueSet(valueSetURI);
			
			// Check if medication substance is in the descendants
			boolean isSubsumed = descendants.stream()
					.anyMatch(coding -> coding.getCode().equals(medicationSubstanceCode));
			
			if (isSubsumed) {
				logger.info("Subsumption match found: medication ingredient {} is subsumed by allergy substance {}", 
						medicationSubstanceCode, allergySubstanceCode);
			}
			
			return isSubsumed;
			
		} catch (Exception e) {
			logger.warn("Failed subsumption check for allergy {} vs medication {}: {}", 
					allergySubstanceCode, medicationSubstanceCode, e.getMessage());
			// On error, fail safe - no match
			return false;
		}
	}

	/**
	 * Creates a CDS Card alert for allergy-medication conflict.
	 */
	private CDSCard createAllergyAlertCard(Coding allergyCoding, Coding medicationCoding, 
	                                        String medicationDisplay, String substanceCode) {
		String uuid = UUID.randomUUID().toString();
		String summary = String.format("ALLERGY ALERT: Patient has known allergy to %s", 
				allergyCoding.getDisplay() != null ? allergyCoding.getDisplay() : allergyCoding.getCode());
		String detail = String.format("The patient has a documented allergy to %s. " +
						"The prescribed medication \"%s\" contains this substance as an active ingredient. " +
						"Please review the patient's allergy history before administering this medication.",
				allergyCoding.getDisplay() != null ? allergyCoding.getDisplay() : allergyCoding.getCode(),
				medicationDisplay);
		
		CDSCard card = new CDSCard(
				uuid,
				summary,
				detail,
				CDSIndicator.critical, // Allergies are critical alerts
				new CDSSource("SNOMED CT Allergy Check"),
				List.of(new CDSReference(List.of(new CDSCoding(medicationCoding.getSystem(), medicationCoding.getCode(), medicationCoding.getDisplay())))),
				List.of(), // No condition references for allergy alerts
				"Allergy Contraindication"
		);
		
		return card;
	}

	/**
	 * Gets the causative agent(s) for an allergy code using lookup with normalForm.
	 * For propensity codes (e.g., "Allergy to atenolol"), this will return the substance (e.g., "Atenolol").
	 * For direct substance codes, this may return empty set or the code itself.
	 * 
	 * Uses CodeSystem/$lookup with property=normalForm to extract the causative agent attribute (246075003).
	 * Example: Lookup 293586001 returns normalForm with "246075003|Causative agent| = 387458008|Aspirin|"
	 */
	private Set<Coding> getCausativeAgentsForAllergy(Coding allergyCoding) {
		try {
			logger.info("Attempting to resolve allergy propensity {} using lookup with normalForm", 
					allergyCoding.getCode());
			
			// Lookup the allergy concept to get its normalForm
			ConceptParameters conceptParameters = tsClient.lookup("http://snomed.info/sct", allergyCoding.getCode());
			SnomedConceptNormalForm normalForm = conceptParameters.getNormalForm();
			
			// Causative agent attribute code
			final String CAUSATIVE_AGENT_ATTRIBUTE = "246075003";
			
			Set<Coding> causativeAgents = new HashSet<>();
			
			// Check attribute groups (causative agent is often in a group)
			for (Map<String, String> group : normalForm.getAttributeGroups()) {
				String causativeAgentCode = group.get(CAUSATIVE_AGENT_ATTRIBUTE);
				if (causativeAgentCode != null) {
					// Lookup the causative agent code to get its display name
					lookupCausativeAgent(causativeAgentCode, causativeAgents);
				}
			}
			
			if (!causativeAgents.isEmpty()) {
				logger.info("Resolved allergy propensity {} to {} causative agent(s)", 
						allergyCoding.getCode(), causativeAgents.size());
				causativeAgents.forEach(agent -> 
					logger.info("  -> Causative agent: {} (Code: {})", 
						agent.getDisplay() != null ? agent.getDisplay() : agent.getCode(), 
						agent.getCode())
				);
			} else {
				logger.info("No causative agents found for allergy code {} in normalForm, using code as-is", 
						allergyCoding.getCode());
			}
			
			return causativeAgents;
		} catch (RestClientException e) {
			// If lookup fails, log and return empty set (will use original code as fallback)
			logger.warn("Failed to get causative agent for allergy code {}: {}", allergyCoding.getCode(), e.getMessage(), e);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Terminology server lookup failed while checking allergies.");
		}
	}

	private void lookupCausativeAgent(String causativeAgentCode, Set<Coding> causativeAgents) {
		// Lookup the causative agent code to get its display name
		try {
			var agentParams = tsClient.lookup("http://snomed.info/sct", causativeAgentCode);
			String display = agentParams.getParameters("display").stream()
					.findFirst()
					.map(p -> p.getValue().toString())
					.orElse(null);
			causativeAgents.add(new Coding("http://snomed.info/sct", causativeAgentCode, display));
		} catch (Exception e) {
			logger.warn("Failed to lookup display for causative agent code {}: {}", causativeAgentCode, e.getMessage());
			causativeAgents.add(new Coding("http://snomed.info/sct", causativeAgentCode, null));
		}
	}

	public void setMedicationOrderSelectTriggers(List<CDSTrigger> medicationOrderSelectTriggers) {
		this.medicationOrderSelectTriggers = medicationOrderSelectTriggers;
	}
}
