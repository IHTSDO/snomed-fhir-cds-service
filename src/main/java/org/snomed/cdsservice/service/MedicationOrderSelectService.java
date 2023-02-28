package org.snomed.cdsservice.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.*;
import org.jetbrains.annotations.NotNull;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSSource;
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

	private List<CDSTrigger> triggers;

	public MedicationOrderSelectService() {
		super("medication-order-select");
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"diagnoses", "Condition?patient={{context.patientId}}&category=problem-list-item&status=active",
				"draftMedicationOrders", "MedicationRequest?patient={{context.patientId}}&status=draft"
		));

		triggers = new ArrayList<>();
		triggers.add(
				// One hardcoded trigger
				// Will be replaced by a set of triggers read in from a spreadsheet at startup
				new CDSTrigger(
						Collections.singleton(new Coding("http://snomed.info/sct", "108600003", "Atorvastatin-containing product")),
						Collections.singleton(new Coding("http://snomed.info/sct", "197321007", "Steatosis of liver")),
						new CDSCard("Contraindication:Atorvastatin-containing product with Steatosis of liver.",
								"Active liver disease is a contraindication for taking Atorvastatin drugs. This patient record has 'Steatosis of liver'.",
								CDSIndicator.warning,
								new CDSSource("https://en.wikipedia.org/wiki/Atorvastatin#Contraindications"))));
	}

	@Override
	public List<CDSCard> call(CDSRequest cdsRequest) {
		Map<String, String> prefetch = cdsRequest.getPrefetchStrings();
		if (prefetch == null || prefetch.get("patient") == null || prefetch.get("diagnoses") == null || prefetch.get("draftMedicationOrders") == null) {
			throw new ResponseStatusException(412, "Request does not include required prefetch information: patient, diagnosis and medications.", null);
		}

		IParser parser = fhirContext.newJsonParser();
		List<Condition> activeDiagnoses = getPrefetchResourcesFromBundle(prefetch, "diagnoses", Condition.class, parser);
		List<MedicationRequest> draftMedicationOrders = getPrefetchResourcesFromBundle(prefetch, "draftMedicationOrders", MedicationRequest.class, parser);

		Set<Coding> activeDiagnosesCodings = getCodings(activeDiagnoses.stream().map(Condition::getCode));
		Set<Coding> draftMedicationOrderCodings = getCodings(draftMedicationOrders.stream().map(MedicationRequest::getMedicationCodeableConcept));

		List<CDSCard> cards = new ArrayList<>();

		for (CDSTrigger trigger : triggers) {
			if (trigger.isTriggered(activeDiagnosesCodings, draftMedicationOrderCodings)) {
				cards.add(trigger.getCard());
			}
		}

		return cards;
	}

	@NotNull
	private static Set<Coding> getCodings(Stream<CodeableConcept> codeableConceptStream) {
		return codeableConceptStream
				.map(CodeableConcept::getCoding)
				.flatMap(Collection::stream)
				.peek(coding -> coding.setDisplay(null))// Strip display before adding to Set of unique codes. Display will not be used in comparison.
				.collect(Collectors.toSet());
	}

	private <T extends Resource> T getPrefetchResource(Map<String, String> prefetch, String name, Class<T> theResourceType, IParser parser) {
		return parser.parseResource(theResourceType, prefetch.get(prefetch.get(name)));
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

//	private class MedicationOrderTriggerCondition extends CDSTrigger {
//		public MedicationOrderTriggerCondition(Collection<CDSCoding> drugCodings, Collection<CDSCoding> findingCodings, CDSCard card) {
////			super(drugCodings, findingCodings, card);
//		}
//	}
}
