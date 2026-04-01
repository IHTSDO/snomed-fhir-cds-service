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
public class PatientViewMedicationSummaryCheckCDSService extends AbstractMedicationSafetyHookCDSService {

	public PatientViewMedicationSummaryCheckCDSService() {
		super("patient-view-medication-summary-check");
		setHook("patient-view");
		setTitle("Patient View Medication Summary Check");
		setDescription("Provides summary medication safety alerts when a patient record is opened.");
		setUsageRequirements("Supports CDS Hooks patient-view with current patient conditions, medications, and allergies provided in prefetch.");
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"conditions", "Condition?patient={{context.patientId}}&category=problem-list-item&clinical-status=active",
				"medications", "MedicationRequest?patient={{context.patientId}}&status=active",
				"allergies", "AllergyIntolerance?patient={{context.patientId}}&clinical-status=active"
		));
	}

	@Override
	protected void validateRequest(CDSRequest cdsRequest) {
		requireHook(cdsRequest, "patient-view");
		requirePrefetch(cdsRequest, "patient");
		requireStringContext(cdsRequest, "patientId", "userId");
	}

	@Override
	protected HookEvaluationContext buildEvaluationContext(CDSRequest cdsRequest, IParser parser) {
		List<Condition> conditions = getPrefetchConditions(cdsRequest, parser);
		List<MedicationRequest> activeMedications = getPrefetchMedicationRequests(cdsRequest, parser);
		List<AllergyIntolerance> allergies = getPrefetchAllergies(cdsRequest, parser);

		return new HookEvaluationContext(
				conditions,
				activeMedications,
				activeMedications,
				activeMedications,
				activeMedications,
				allergies,
				true,
				true,
				true,
				true
		);
	}
}
