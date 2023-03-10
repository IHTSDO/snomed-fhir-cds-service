package org.snomed.cdsservice.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import jakarta.annotation.PostConstruct;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class MedicationOrderSelectService extends CDSService {

	@Autowired
	private FhirContext fhirContext;

	@Autowired
	private MedicationConditionRuleLoaderService ruleLoaderService;

	private List<CDSTrigger> triggers;

	public MedicationOrderSelectService() {
		super("medication-order-select");
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"conditions", "Condition?patient={{context.patientId}}&category=problem-list-item&status=active",
				"draftMedicationRequests", "MedicationRequest?patient={{context.patientId}}&status=draft"
		));
	}

	@PostConstruct
	public void init() throws ServiceException {
		triggers = ruleLoaderService.loadTriggers();
	}

	@Override
	public List<CDSCard> call(CDSRequest cdsRequest) {
		Map<String, String> prefetch = cdsRequest.getPrefetchStrings();
		if (prefetch == null || prefetch.get("patient") == null || prefetch.get("conditions") == null || prefetch.get("draftMedicationRequests") == null) {
			throw new ResponseStatusException(412, "Request does not include required prefetch information: patient, diagnosis and medications.", null);
		}

		IParser parser = fhirContext.newJsonParser();
		List<Condition> activeDiagnoses = getPrefetchResourcesFromBundle(prefetch, "conditions", Condition.class, parser);
		List<MedicationRequest> draftMedicationOrders = getPrefetchResourcesFromBundle(prefetch, "draftMedicationRequests", MedicationRequest.class, parser);

		Set<Coding> activeDiagnosesCodings = getCodings(activeDiagnoses.stream().map(Condition::getCode));
		Set<Coding> draftMedicationOrderCodings = getCodings(draftMedicationOrders.stream().map(MedicationRequest::getMedicationCodeableConcept));

		List<CDSCard> cards = new ArrayList<>();

		for (CDSTrigger trigger : triggers) {
			CDSCard card = trigger.createRelevantCard(activeDiagnosesCodings, draftMedicationOrderCodings);
			if (card != null) {
				cards.add(card);
			}
		}

		return cards;
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

	public void setTriggers(List<CDSTrigger> triggers) {
		this.triggers = triggers;
	}
}
