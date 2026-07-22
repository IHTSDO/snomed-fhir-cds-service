package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.CriterionOperator;
import org.snomed.cdsservice.model.rules.CriterionType;
import org.snomed.cdsservice.model.rules.TruthValue;
import org.snomed.cdsservice.service.rules.CodeKey;
import org.snomed.cdsservice.service.rules.CodeResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Evaluates whether a coded FHIR resource is present (or, with {@code not_exists}, absent).
 * <p>
 * The evaluator is driven by the criterion's {@code resource_type} so that beyond the {@code Condition}
 * used by the current demonstration files it also serves Procedure, Immunization, MedicationRequest and
 * AllergyIntolerance — enabling care-gap style use cases (for example "is the screening procedure
 * recorded?") without changing the engine. Only clinically relevant resources count: entered-in-error or
 * inactive/historical resources are ignored.
 * <p>
 * Presence is affirmative evidence, but absence is treated as {@link TruthValue#UNKNOWN} rather than
 * FALSE, because a CDS Hooks prefetch may be incomplete. To assert a genuine absence the content should
 * use an explicit context value instead (see the ABPM/HBPM-unavailable pattern).
 */
public class CodedResourcePresentCriterionEvaluator implements CriterionEvaluator {

	private final CodeResolver codeResolver;

	public CodedResourcePresentCriterionEvaluator(CodeResolver codeResolver) {
		this.codeResolver = codeResolver;
	}

	@Override
	public CriterionType supportedType() {
		return CriterionType.CODED_RESOURCE_PRESENT;
	}

	@Override
	public CriterionEvaluationResult evaluate(Criterion criterion, RuleEvaluationContext context) {
		Set<CodeKey> acceptableCodes = codeResolver.acceptableCodes(criterion);
		String resourceType = criterion.resourceType();

		List<Resource> matches = new ArrayList<>();
		for (Resource resource : context.resources()) {
			if (!resource.getResourceType().name().equalsIgnoreCase(resourceType)) {
				continue;
			}
			if (!isClinicallyRelevant(resource)) {
				continue;
			}
			if (conceptsMatch(extractConcepts(resource), acceptableCodes)) {
				matches.add(resource);
			}
		}

		boolean present = !matches.isEmpty();
		if (criterion.operator() == CriterionOperator.NOT_EXISTS) {
			return present
					? CriterionEvaluationResult.of(criterion, TruthValue.FALSE, matches, "A matching %s is present.".formatted(resourceType))
					: CriterionEvaluationResult.of(criterion, TruthValue.UNKNOWN, List.of(), "No matching %s was supplied; absence cannot be confirmed from prefetch.".formatted(resourceType));
		}
		return present
				? CriterionEvaluationResult.of(criterion, TruthValue.TRUE, matches, "A matching %s is present.".formatted(resourceType))
				: CriterionEvaluationResult.of(criterion, TruthValue.UNKNOWN, List.of(), "No matching %s was supplied.".formatted(resourceType));
	}

	private boolean conceptsMatch(List<CodeableConcept> concepts, Set<CodeKey> acceptableCodes) {
		for (CodeableConcept concept : concepts) {
			for (Coding coding : concept.getCoding()) {
				if (acceptableCodes.contains(CodeKey.of(coding))) {
					return true;
				}
			}
		}
		return false;
	}

	private List<CodeableConcept> extractConcepts(Resource resource) {
		List<CodeableConcept> concepts = new ArrayList<>();
		if (resource instanceof Condition condition && condition.hasCode()) {
			concepts.add(condition.getCode());
		} else if (resource instanceof Procedure procedure && procedure.hasCode()) {
			concepts.add(procedure.getCode());
		} else if (resource instanceof Immunization immunization && immunization.hasVaccineCode()) {
			concepts.add(immunization.getVaccineCode());
		} else if (resource instanceof MedicationRequest request && request.hasMedicationCodeableConcept()) {
			concepts.add(request.getMedicationCodeableConcept());
		} else if (resource instanceof AllergyIntolerance allergy && allergy.hasCode()) {
			concepts.add(allergy.getCode());
		} else if (resource instanceof Observation observation && observation.hasCode()) {
			concepts.add(observation.getCode());
		}
		return concepts;
	}

	private boolean isClinicallyRelevant(Resource resource) {
		if (resource instanceof Condition condition) {
			return !isEnteredInError(condition.hasVerificationStatus() ? condition.getVerificationStatus() : null)
					&& isActiveClinicalStatus(condition.hasClinicalStatus() ? condition.getClinicalStatus() : null);
		}
		if (resource instanceof AllergyIntolerance allergy) {
			return !isEnteredInError(allergy.hasVerificationStatus() ? allergy.getVerificationStatus() : null)
					&& isActiveClinicalStatus(allergy.hasClinicalStatus() ? allergy.getClinicalStatus() : null);
		}
		if (resource instanceof Procedure procedure) {
			Procedure.ProcedureStatus status = procedure.getStatus();
			return status == Procedure.ProcedureStatus.COMPLETED || status == Procedure.ProcedureStatus.INPROGRESS;
		}
		if (resource instanceof Immunization immunization) {
			return immunization.getStatus() == Immunization.ImmunizationStatus.COMPLETED;
		}
		if (resource instanceof MedicationRequest request) {
			return request.getStatus() == MedicationRequest.MedicationRequestStatus.ACTIVE;
		}
		return true;
	}

	private boolean isEnteredInError(CodeableConcept verificationStatus) {
		return verificationStatus != null && verificationStatus.getCoding().stream()
				.anyMatch(coding -> "entered-in-error".equalsIgnoreCase(coding.getCode()));
	}

	private boolean isActiveClinicalStatus(CodeableConcept clinicalStatus) {
		if (clinicalStatus == null || clinicalStatus.getCoding().isEmpty()) {
			// A missing clinical status is treated as relevant rather than excluding the resource.
			return true;
		}
		return clinicalStatus.getCoding().stream().anyMatch(coding -> {
			String code = coding.getCode();
			return "active".equalsIgnoreCase(code) || "recurrence".equalsIgnoreCase(code) || "relapse".equalsIgnoreCase(code);
		});
	}
}
