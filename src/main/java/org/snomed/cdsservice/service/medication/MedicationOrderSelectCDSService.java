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
public class MedicationOrderSelectCDSService extends AbstractMedicationSafetyHookCDSService {

	public MedicationOrderSelectCDSService() {
		super("medication-order-select");
		setHook("order-select");
		setTitle("Medication Order Select");
		setDescription("Returns medication prescribing alerts for contraindications, interactions, excessive dosage, and allergy conflicts.");
		setUsageRequirements("Supports medication prescribing workflows using CDS Hooks order-select with selected draft MedicationRequest orders.");
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"conditions", "Condition?patient={{context.patientId}}&category=problem-list-item&clinical-status=active",
				"medications", "MedicationRequest?patient={{context.patientId}}&status=active",
				"allergies", "AllergyIntolerance?patient={{context.patientId}}&clinical-status=active"
		));
	}

	@Override
	protected void validateRequest(CDSRequest cdsRequest) {
		requireHook(cdsRequest, "order-select");
		requirePrefetch(cdsRequest, "patient", "conditions");
		requireContext(cdsRequest, "draftOrders");
		if (cdsRequest.getContextStringList("selections") == null || cdsRequest.getContextStringList("selections").isEmpty()) {
			throw badRequest("Request context must include one or more selections.");
		}
		if (getSelectedMedicationRequests(cdsRequest, fhirContext.newJsonParser()).isEmpty()) {
			throw badRequest("Selections must resolve to MedicationRequest resources in context.draftOrders.");
		}
	}

	@Override
	protected HookEvaluationContext buildEvaluationContext(CDSRequest cdsRequest, IParser parser) {
		List<Condition> conditions = getPrefetchConditions(cdsRequest, parser);
		List<MedicationRequest> selectedDraftMedications = getSelectedMedicationRequests(cdsRequest, parser);
		List<MedicationRequest> activeMedications = getPrefetchMedicationRequests(cdsRequest, parser);
		List<AllergyIntolerance> allergies = getPrefetchAllergies(cdsRequest, parser);
		List<MedicationRequest> referenceMedications = distinctMedicationRequests(
				java.util.stream.Stream.concat(selectedDraftMedications.stream(), activeMedications.stream()).toList()
		);

		return new HookEvaluationContext(
				conditions,
				selectedDraftMedications,
				referenceMedications,
				referenceMedications,
				selectedDraftMedications,
				allergies,
				true,
				true,
				true,
				true
		);
	}
}
