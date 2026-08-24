package org.snomed.cdsservice.service.medication;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ProblemListItemCreateMedicationCheckCDSService extends AbstractMedicationSafetyHookCDSService {

	public ProblemListItemCreateMedicationCheckCDSService() {
		super("problem-list-item-create-medication-check");
		setHook("problem-list-item-create");
		setTitle("Problem List Item Create Medication Check");
		setDescription("Checks newly added problem list items against the patient’s existing medications.");
		setUsageRequirements("Supports CDS Hooks problem-list-item-create with newly finalized Condition resources and active medications in prefetch.");
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"medications", "MedicationRequest?patient={{context.patientId}}&status=active"
		));
	}

	@Override
	protected void validateRequest(CDSRequest cdsRequest) {
		requireHook(cdsRequest, "problem-list-item-create");
		requirePrefetch(cdsRequest, "patient", "medications");
		requireContext(cdsRequest, "conditions");
		if (getConditionsFromContextBundle(cdsRequest, "conditions", fhirContext.newJsonParser()).isEmpty()) {
			throw badRequest("Request context conditions must include one or more Condition resources.");
		}
	}

	@Override
	protected HookEvaluationContext buildEvaluationContext(CDSRequest cdsRequest, IParser parser) {
		List<Condition> newConditions = getConditionsFromContextBundle(cdsRequest, "conditions", parser);
		List<MedicationRequest> activeMedications = getPrefetchMedicationRequests(cdsRequest, parser);

		return new HookEvaluationContext(
				newConditions,
				activeMedications,
				activeMedications,
				activeMedications,
				List.of(),
				List.of(),
				true,
				false,
				false,
				false
		);
	}
}
