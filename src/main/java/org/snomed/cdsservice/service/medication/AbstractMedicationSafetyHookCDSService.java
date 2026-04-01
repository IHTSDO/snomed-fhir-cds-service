package org.snomed.cdsservice.service.medication;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.snomed.cdsservice.service.CDSService;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.snomed.cdsservice.service.tsclient.ConceptParameters;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.service.tsclient.SnomedConceptNormalForm;
import org.snomed.cdsservice.util.SnomedValueSetUtil;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class AbstractMedicationSafetyHookCDSService extends CDSService {

	protected final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	protected FhirContext fhirContext;

	@Autowired
	protected ObjectMapper objectMapper;

	@Autowired
	private MedicationConditionRuleLoaderService ruleLoaderService;

	@Autowired
	private MedicationCombinationRuleLoaderService medicationRuleLoaderService;

	@Autowired
	private SnomedMedicationDefinedDailyDoseService definedDailyDoseService;

	@Autowired
	protected FHIRTerminologyServerClient tsClient;

	private List<CDSTrigger> medicationConditionTriggers;
	private List<CDSTrigger> drugDrugInteractionTriggers;

	protected AbstractMedicationSafetyHookCDSService(String id) {
		super(id);
	}

	@PostConstruct
	public void initMedicationSafetyTriggers() throws ServiceException {
		medicationConditionTriggers = ruleLoaderService.loadTriggers();
		drugDrugInteractionTriggers = medicationRuleLoaderService.loadTriggers();
	}

	@Override
	public List<CDSCard> call(CDSRequest cdsRequest) {
		validateRequest(cdsRequest);
		IParser parser = fhirContext.newJsonParser();
		HookEvaluationContext evaluationContext = buildEvaluationContext(cdsRequest, parser);
		return evaluateContext(evaluationContext);
	}

	protected abstract void validateRequest(CDSRequest cdsRequest);

	protected abstract HookEvaluationContext buildEvaluationContext(CDSRequest cdsRequest, IParser parser);

	protected List<Condition> getPrefetchConditions(CDSRequest cdsRequest, IParser parser) {
		return getPrefetchResourcesFromBundle(cdsRequest.getPrefetchStrings(), "conditions", Condition.class, parser);
	}

	protected List<AllergyIntolerance> getPrefetchAllergies(CDSRequest cdsRequest, IParser parser) {
		return getPrefetchResourcesFromBundle(cdsRequest.getPrefetchStrings(), "allergies", AllergyIntolerance.class, parser);
	}

	protected List<MedicationRequest> getPrefetchMedicationRequests(CDSRequest cdsRequest, IParser parser) {
		return getPrefetchResourcesFromBundle(cdsRequest.getPrefetchStrings(), "medications", MedicationRequest.class, parser);
	}

	protected List<MedicationRequest> getMedicationRequestsFromContextBundle(CDSRequest cdsRequest, String contextKey, IParser parser) {
		Bundle bundle = getContextBundle(cdsRequest, contextKey, parser);
		if (bundle == null) {
			return List.of();
		}
		return getResourcesByType(bundle, ResourceType.MedicationRequest).stream()
				.map(component -> (MedicationRequest) component.getResource())
				.toList();
	}

	protected List<Condition> getConditionsFromContextBundle(CDSRequest cdsRequest, String contextKey, IParser parser) {
		Bundle bundle = getContextBundle(cdsRequest, contextKey, parser);
		if (bundle == null) {
			return List.of();
		}
		return getResourcesByType(bundle, ResourceType.Condition).stream()
				.map(component -> (Condition) component.getResource())
				.toList();
	}

	protected Bundle getContextBundle(CDSRequest cdsRequest, String contextKey, IParser parser) {
		try {
			String bundleJson = cdsRequest.getContextValueAsJson(contextKey, objectMapper);
			if (bundleJson == null) {
				return null;
			}
			return parser.parseResource(Bundle.class, bundleJson);
		} catch (JsonProcessingException e) {
			throw badRequest("Request context " + contextKey + " could not be parsed as JSON.", e);
		}
	}

	protected <T extends Resource> T getContextResource(CDSRequest cdsRequest, String contextKey, Class<T> resourceType, IParser parser) {
		try {
			String resourceJson = cdsRequest.getContextValueAsJson(contextKey, objectMapper);
			if (resourceJson == null) {
				return null;
			}
			return parser.parseResource(resourceType, resourceJson);
		} catch (JsonProcessingException e) {
			throw badRequest("Request context " + contextKey + " could not be parsed as JSON.", e);
		}
	}

	protected List<MedicationRequest> getSelectedMedicationRequests(CDSRequest cdsRequest, IParser parser) {
		Bundle draftOrders = getContextBundle(cdsRequest, "draftOrders", parser);
		List<String> selections = cdsRequest.getContextStringList("selections");
		if (draftOrders == null || selections == null || selections.isEmpty()) {
			return List.of();
		}

		Set<String> selectedValues = new HashSet<>(selections);
		List<MedicationRequest> selectedMedicationRequests = new ArrayList<>();
		for (Bundle.BundleEntryComponent component : draftOrders.getEntry()) {
			Resource resource = component.getResource();
			if (!(resource instanceof MedicationRequest medicationRequest)) {
				continue;
			}

			String id = medicationRequest.getIdElement().getIdPart();
			String relativeReference = id == null ? null : "MedicationRequest/" + id;
			String fullUrl = component.getFullUrl();
			if ((id != null && selectedValues.contains(id))
					|| (relativeReference != null && selectedValues.contains(relativeReference))
					|| (fullUrl != null && selectedValues.contains(fullUrl))) {
				selectedMedicationRequests.add(medicationRequest);
			}
		}
		return selectedMedicationRequests;
	}

	protected void requireHook(CDSRequest cdsRequest, String expectedHook) {
		if (!expectedHook.equals(cdsRequest.getHook())) {
			throw badRequest("This service supports CDS Hooks " + expectedHook + " requests only.");
		}
	}

	protected void requireContext(CDSRequest cdsRequest, String... requiredKeys) {
		if (cdsRequest.getContext() == null) {
			throw badRequest("Request does not include required context.");
		}
		for (String key : requiredKeys) {
			try {
				if (cdsRequest.getContextValueAsJson(key, objectMapper) == null) {
					throw badRequest("Request context must include " + key + ".");
				}
			} catch (JsonProcessingException e) {
				throw badRequest("Request context " + key + " could not be parsed as JSON.", e);
			}
		}
	}

	protected void requireStringContext(CDSRequest cdsRequest, String... requiredKeys) {
		if (cdsRequest.getContext() == null) {
			throw badRequest("Request does not include required context.");
		}
		for (String key : requiredKeys) {
			if (cdsRequest.getContextString(key) == null) {
				throw badRequest("Request context must include " + key + ".");
			}
		}
	}

	protected void requirePrefetch(CDSRequest cdsRequest, String... requiredKeys) {
		Map<String, String> prefetch = cdsRequest.getPrefetchStrings();
		if (prefetch == null) {
			throw badRequest("Request does not include required prefetch information.");
		}
		for (String key : requiredKeys) {
			if (prefetch.get(key) == null) {
				throw badRequest("Request prefetch must include " + key + ".");
			}
		}
	}

	protected RuntimeException badRequest(String message) {
		return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
	}

	protected RuntimeException badRequest(String message, Exception cause) {
		return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, message, cause);
	}

	protected RuntimeException preconditionFailed(String message) {
		return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.PRECONDITION_FAILED, message);
	}

	protected List<CDSCard> evaluateContext(HookEvaluationContext context) {
		List<CDSCard> cards = new ArrayList<>();
		List<MedicationRequest> referenceMedicationRequests = distinctMedicationRequests(context.referenceMedicationRequests());
		List<Condition> referenceConditions = context.referenceConditions();

		if (context.conditionTriggersEnabled()
				&& !context.targetMedicationRequests().isEmpty()
				&& !context.referenceConditions().isEmpty()) {
			Set<Coding> conditionCodings = getCodings(referenceConditions.stream().map(Condition::getCode));
			Set<Coding> targetMedicationCodings = getCodings(context.targetMedicationRequests().stream().map(MedicationRequest::getMedicationCodeableConcept));

			for (CDSTrigger trigger : medicationConditionTriggers) {
				CDSCard card = trigger.createRelevantCard(conditionCodings, targetMedicationCodings);
				if (card != null) {
					addCodesFromOtherCodingSystemsForDraftMedications(card.getReferenceMedications(), referenceMedicationRequests);
					addCodesFromOtherCodingSystemsForConditions(card.getReferenceConditions(), referenceConditions);
					cards.add(card);
				}
			}
		}

		if (context.interactionChecksEnabled()
				&& !context.targetMedicationRequests().isEmpty()
				&& !context.comparisonMedicationRequests().isEmpty()) {
			Set<Coding> targetMedicationCodings = getCodings(context.targetMedicationRequests().stream().map(MedicationRequest::getMedicationCodeableConcept));
			Set<Coding> comparisonMedicationCodings = getCodings(context.comparisonMedicationRequests().stream().map(MedicationRequest::getMedicationCodeableConcept));

			drugDrugInteractionTriggers.forEach(trigger -> {
				CDSCard card = trigger.createRelevantCard(targetMedicationCodings, comparisonMedicationCodings);
				if (card != null) {
					addCodesFromOtherCodingSystemsForDraftMedications(card.getReferenceMedications(), referenceMedicationRequests);
					cards.add(card);
				}
			});
		}

		if (context.doseChecksEnabled() && !context.doseMedicationRequests().isEmpty()) {
			cards.addAll(definedDailyDoseService.checkMedications(context.doseMedicationRequests()));
		}

		if (context.allergyChecksEnabled()
				&& !context.targetMedicationRequests().isEmpty()
				&& !context.allergies().isEmpty()) {
			Set<Coding> allergySubstanceCodings = getAllergyCodings(context.allergies());
			if (!allergySubstanceCodings.isEmpty()) {
				cards.addAll(checkAllergyMedicationConflicts(allergySubstanceCodings, context.targetMedicationRequests()));
			}
		}

		return cards;
	}

	protected List<MedicationRequest> distinctMedicationRequests(List<MedicationRequest> medicationRequests) {
		Map<String, MedicationRequest> distinct = new LinkedHashMap<>();
		for (MedicationRequest medicationRequest : medicationRequests) {
			String key = medicationRequest.getIdElement().getIdPart();
			if (key == null || key.isBlank()) {
				key = UUID.randomUUID().toString();
			}
			distinct.putIfAbsent(key, medicationRequest);
		}
		return new ArrayList<>(distinct.values());
	}

	private void addCodesFromOtherCodingSystemsForConditions(List<CDSReference> referenceConditions, List<Condition> activeDiagnoses) {
		referenceConditions.forEach(referenceCondition -> {
			CDSCoding cdsCoding = referenceCondition.getCoding().get(0);
			Optional<Condition> optionalCondition = activeDiagnoses.stream().filter(condition -> {
				List<Coding> codingList = condition.getCode().getCoding();
				Optional<Coding> optionalCoding = codingList.stream().filter(coding -> coding.getCode().equals(cdsCoding.getCode()) && coding.getSystem().equals(cdsCoding.getSystem())).findFirst();
				return optionalCoding.isPresent();
			}).findFirst();
			optionalCondition.ifPresent(getCDSReferenceConditionConsumer(referenceCondition));
		});
	}

	private void addCodesFromOtherCodingSystemsForDraftMedications(List<CDSReference> referenceMedications, List<MedicationRequest> draftMedicationOrders) {
		referenceMedications.forEach(referenceMedication -> {
			CDSCoding cdsCoding = referenceMedication.getCoding().get(0);
			Optional<MedicationRequest> optionalMedicationRequest = draftMedicationOrders.stream().filter(medicationRequest -> {
				List<Coding> codingList = medicationRequest.getMedicationCodeableConcept().getCoding();
				Optional<Coding> optionalCoding = codingList.stream().filter(coding -> coding.getCode().equals(cdsCoding.getCode()) && coding.getSystem().equals(cdsCoding.getSystem())).findFirst();
				return optionalCoding.isPresent();
			}).findFirst();
			optionalMedicationRequest.ifPresent(getCDSReferenceMedicationRequestConsumer(referenceMedication));
		});
	}

	@NotNull
	private Consumer<Condition> getCDSReferenceConditionConsumer(CDSReference reference) {
		return condition -> reference.setCoding(condition.getCode().getCoding().stream().map(getCodingCDSCodingFunction()).collect(Collectors.toList()));
	}

	@NotNull
	private Consumer<MedicationRequest> getCDSReferenceMedicationRequestConsumer(CDSReference reference) {
		return medicationRequest -> reference.setCoding(medicationRequest.getMedicationCodeableConcept().getCoding().stream().map(getCodingCDSCodingFunction()).collect(Collectors.toList()));
	}

	@NotNull
	private Function<Coding, CDSCoding> getCodingCDSCodingFunction() {
		return coding -> new CDSCoding(coding.getSystem(), coding.getCode(), coding.getDisplay());
	}

	@NotNull
	protected static Set<Coding> getCodings(Stream<CodeableConcept> codeableConceptStream) {
		return codeableConceptStream
				.map(CodeableConcept::getCoding)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());
	}

	protected <T extends Resource> List<T> getPrefetchResourcesFromBundle(Map<String, String> prefetch, String name, Class<T> theResourceType, IParser parser) {
		if (prefetch == null || prefetch.get(name) == null) {
			return List.of();
		}
		Bundle bundle = parser.parseResource(Bundle.class, prefetch.get(name));
		List<T> resources = new ArrayList<>();
		for (Bundle.BundleEntryComponent component : bundle.getEntry()) {
			Resource resource = component.getResource();
			if (resource != null && resource.getResourceType().name().equals(theResourceType.getSimpleName())) {
				resources.add(theResourceType.cast(resource));
			}
		}
		return resources;
	}

	private Set<Coding> getAllergyCodings(List<AllergyIntolerance> allergies) {
		Set<Coding> codings = new HashSet<>();

		for (AllergyIntolerance allergy : allergies) {
			boolean substancesExtracted = false;

			if (allergy.hasCode() && allergy.getCode().hasCoding()) {
				for (Coding coding : allergy.getCode().getCoding()) {
					if (coding.getSystem() != null && coding.getSystem().contains("snomed.info/sct")) {
						Set<Coding> causativeAgents = getCausativeAgentsForAllergy(coding);
						if (!causativeAgents.isEmpty()) {
							codings.addAll(causativeAgents);
							substancesExtracted = true;
						} else {
							codings.add(coding);
							substancesExtracted = true;
						}
					}
				}
			}

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

	private List<CDSCard> checkAllergyMedicationConflicts(Set<Coding> allergyCodings, List<MedicationRequest> medicationRequests) {
		List<CDSCard> allergyCards = new ArrayList<>();

		for (MedicationRequest medicationRequest : medicationRequests) {
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
				var conceptParameters = tsClient.lookup("http://snomed.info/sct", medicationCode);

				if (conceptParameters == null || conceptParameters.getNormalForm() == null) {
					continue;
				}

				Set<String> medicationSubstances = extractMedicationSubstances(conceptParameters.getNormalForm());

				if (medicationSubstances.isEmpty()) {
					continue;
				}

				for (String substanceCode : medicationSubstances) {
					for (Coding allergyCoding : allergyCodings) {
						if (isAllergyMatch(allergyCoding.getCode(), substanceCode)) {
							CDSCard allergyCard = createAllergyAlertCard(
									allergyCoding,
									snomedMedication.get(),
									medicationDisplay
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

	private Set<String> extractMedicationSubstances(SnomedConceptNormalForm normalForm) {
		Set<String> substances = new HashSet<>();

		for (Map<String, String> attributeGroup : normalForm.getAttributeGroups()) {
			String substance = attributeGroup.get("732943007");
			if (substance != null) {
				substances.add(substance);
			}
		}

		return substances;
	}

	private boolean isAllergyMatch(String allergySubstanceCode, String medicationSubstanceCode) {
		if (allergySubstanceCode.equals(medicationSubstanceCode)) {
			return true;
		}

		try {
			String ecl = String.format("<< %s", allergySubstanceCode);
			String valueSetURI = SnomedValueSetUtil.getSnomedECLValueSetURI(ecl);
			Collection<Coding> descendants = tsClient.expandValueSet(valueSetURI);
			return descendants.stream().anyMatch(coding -> coding.getCode().equals(medicationSubstanceCode));
		} catch (Exception e) {
			logger.warn("Failed subsumption check for allergy {} vs medication {}: {}",
					allergySubstanceCode, medicationSubstanceCode, e.getMessage());
			return false;
		}
	}

	private CDSCard createAllergyAlertCard(Coding allergyCoding, Coding medicationCoding, String medicationDisplay) {
		String uuid = UUID.randomUUID().toString();
		String summary = String.format("ALLERGY ALERT: Patient has known allergy to %s",
				allergyCoding.getDisplay() != null ? allergyCoding.getDisplay() : allergyCoding.getCode());
		String detail = String.format("The patient has a documented allergy to %s. " +
						"The prescribed medication \"%s\" contains this substance as an active ingredient. " +
						"Please review the patient's allergy history before administering this medication.",
				allergyCoding.getDisplay() != null ? allergyCoding.getDisplay() : allergyCoding.getCode(),
				medicationDisplay);

		return new CDSCard(
				uuid,
				summary,
				detail,
				CDSIndicator.critical,
				new CDSSource("SNOMED CT Allergy Check"),
				List.of(new CDSReference(List.of(new CDSCoding(medicationCoding.getSystem(), medicationCoding.getCode(), medicationCoding.getDisplay())))),
				List.of(),
				"Allergy Contraindication"
		);
	}

	private Set<Coding> getCausativeAgentsForAllergy(Coding allergyCoding) {
		try {
			ConceptParameters conceptParameters = tsClient.lookup("http://snomed.info/sct", allergyCoding.getCode());
			SnomedConceptNormalForm normalForm = conceptParameters.getNormalForm();

			final String CAUSATIVE_AGENT_ATTRIBUTE = "246075003";
			Map<String, String> attributes = normalForm.getAttributes();
			Set<Coding> causativeAgents = new HashSet<>();

			if (attributes.containsKey(CAUSATIVE_AGENT_ATTRIBUTE)) {
				String substanceCode = attributes.get(CAUSATIVE_AGENT_ATTRIBUTE);
				causativeAgents.add(createSubstanceCoding(substanceCode));
			}

			for (Map<String, String> attributeGroup : normalForm.getAttributeGroups()) {
				String substanceCode = attributeGroup.get(CAUSATIVE_AGENT_ATTRIBUTE);
				if (substanceCode != null) {
					causativeAgents.add(createSubstanceCoding(substanceCode));
				}
			}
			return causativeAgents;
		} catch (Exception e) {
			logger.warn("Failed to resolve causative agent for allergy {}: {}", allergyCoding.getCode(), e.getMessage());
			return Collections.emptySet();
		}
	}

	private Coding createSubstanceCoding(String substanceCode) {
		try {
			ConceptParameters substanceParams = tsClient.lookup("http://snomed.info/sct", substanceCode);
			String display = substanceParams.getParameter("display") != null
					? substanceParams.getParameter("display").getValue().toString()
					: null;
			return new Coding("http://snomed.info/sct", substanceCode, display);
		} catch (Exception e) {
			return new Coding("http://snomed.info/sct", substanceCode, null);
		}
	}

	public void setMedicationOrderSelectTriggers(List<CDSTrigger> medicationOrderSelectTriggers) {
		this.medicationConditionTriggers = medicationOrderSelectTriggers;
	}

	protected record HookEvaluationContext(
			List<Condition> referenceConditions,
			List<MedicationRequest> targetMedicationRequests,
			List<MedicationRequest> comparisonMedicationRequests,
			List<MedicationRequest> referenceMedicationRequests,
			List<MedicationRequest> doseMedicationRequests,
			List<AllergyIntolerance> allergies,
			boolean conditionTriggersEnabled,
			boolean interactionChecksEnabled,
			boolean doseChecksEnabled,
			boolean allergyChecksEnabled
	) {}
}
