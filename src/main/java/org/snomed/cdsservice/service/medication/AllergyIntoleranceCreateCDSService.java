package org.snomed.cdsservice.service.medication;

import ca.uhn.fhir.parser.IParser;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AllergyIntoleranceCreateCDSService extends AbstractMedicationSafetyHookCDSService {

	public AllergyIntoleranceCreateCDSService() {
		super("allergyintolerance-create-medication-check");
		setHook("allergyintolerance-create");
		setTitle("AllergyIntolerance Create Medication Check");
		setDescription("Checks a newly created allergy or intolerance against the patient's existing medications.");
		setUsageRequirements("Supports CDS Hooks allergyintolerance-create with a new AllergyIntolerance resource in context and active medications in prefetch.");
		setPrefetch(Map.of(
				"patient", "Patient/{{context.patientId}}",
				"medications", "MedicationRequest?patient={{context.patientId}}&status=active"
		));
	}

	@Override
	protected void validateRequest(CDSRequest cdsRequest) {
		requireHook(cdsRequest, "allergyintolerance-create");
		requirePrefetch(cdsRequest, "patient", "medications");
		requireStringContext(cdsRequest, "patientId", "userId");
		requireContext(cdsRequest, "allergyIntolerance");
		if (getContextResource(cdsRequest, "allergyIntolerance", AllergyIntolerance.class, fhirContext.newJsonParser()) == null) {
			throw badRequest("Request context allergyIntolerance must include a valid AllergyIntolerance resource.");
		}
	}

	@Override
	protected HookEvaluationContext buildEvaluationContext(CDSRequest cdsRequest, IParser parser) {
		AllergyIntolerance newAllergy = getContextResource(cdsRequest, "allergyIntolerance", AllergyIntolerance.class, parser);
		List<MedicationRequest> activeMedications = getPrefetchMedicationRequests(cdsRequest, parser);

		return new HookEvaluationContext(
				List.of(),
				activeMedications,
				activeMedications,
				activeMedications,
				List.of(),
				newAllergy == null ? List.of() : List.of(newAllergy),
				false,
				false,
				false,
				true
		);
	}
}
