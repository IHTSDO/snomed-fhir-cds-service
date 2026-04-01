package org.snomed.cdsservice.service.medication;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class MedicationOrderSignCDSService extends AbstractMedicationSafetyHookCDSService {

	public MedicationOrderSignCDSService() {
		super("medication-order-sign");
		setHook("order-sign");
		setTitle("Medication Order Sign");
		setDescription("Returns final medication prescribing alerts before draft orders are signed.");
		setUsageRequirements("Supports medication prescribing workflows using CDS Hooks order-sign with draft MedicationRequest orders.");
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"conditions", "Condition?patient={{context.patientId}}&category=problem-list-item&clinical-status=active",
				"medications", "MedicationRequest?patient={{context.patientId}}&status=active",
				"allergies", "AllergyIntolerance?patient={{context.patientId}}&clinical-status=active"
		));
	}

	@Override
	protected void validateRequest(CDSRequest cdsRequest) {
		requireHook(cdsRequest, "order-sign");
		requirePrefetch(cdsRequest, "patient", "conditions");
		requireContext(cdsRequest, "draftOrders");
		if (getMedicationRequestsFromContextBundle(cdsRequest, "draftOrders", fhirContext.newJsonParser()).isEmpty()) {
			throw badRequest("Request context draftOrders must include one or more MedicationRequest resources.");
		}
	}

	@Override
	protected HookEvaluationContext buildEvaluationContext(CDSRequest cdsRequest, IParser parser) {
		List<Condition> conditions = getPrefetchConditions(cdsRequest, parser);
		List<MedicationRequest> draftMedications = getMedicationRequestsFromContextBundle(cdsRequest, "draftOrders", parser);
		List<MedicationRequest> activeMedications = getPrefetchMedicationRequests(cdsRequest, parser);
		List<AllergyIntolerance> allergies = getPrefetchAllergies(cdsRequest, parser);
		List<MedicationRequest> referenceMedications = distinctMedicationRequests(
				java.util.stream.Stream.concat(draftMedications.stream(), activeMedications.stream()).toList()
		);

		return new HookEvaluationContext(
				conditions,
				draftMedications,
				referenceMedications,
				referenceMedications,
				draftMedications,
				allergies,
				true,
				true,
				true,
				true
		);
	}
}
