package org.snomed.cdsservice.service.medication;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSCoding;
import org.snomed.cdsservice.model.CDSReference;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.snomed.cdsservice.service.*;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MedicationOrderSelectCDSService extends CDSService {

	@Autowired
	private FhirContext fhirContext;

	@Autowired
	private MedicationConditionRuleLoaderService ruleLoaderService;

	@Autowired
	private MedicationCombinationRuleLoaderService medicationRuleLoaderService;

	@Autowired
	private SnomedMedicationDefinedDailyDoseService definedDailyDoseService;

	private List<CDSTrigger> medicationOrderSelectTriggers;

	private List<CDSTrigger> drugDrugInteractionTriggers;

	public MedicationOrderSelectCDSService() {
		super("medication-order-select");
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"conditions", "Condition?patient={{context.patientId}}&category=problem-list-item&status=active",
				"draftMedicationRequests", "MedicationRequest?patient={{context.patientId}}&status=draft"
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

		Set<Coding> activeDiagnosesCodings = getCodings(activeDiagnoses.stream().map(Condition::getCode));
		Set<Coding> draftMedicationOrderCodings = getCodings(medicationRequests.stream().map(MedicationRequest::getMedicationCodeableConcept));

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
			if (resource.getResourceType().name().equals(theResourceType.getSimpleName())) {
				resources.add((T) resource);
			}
		}
		return resources;
	}

	public void setMedicationOrderSelectTriggers(List<CDSTrigger> medicationOrderSelectTriggers) {
		this.medicationOrderSelectTriggers = medicationOrderSelectTriggers;
	}
}
