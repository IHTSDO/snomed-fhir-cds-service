package org.snomed.cdsservice.model.rules;

import org.hl7.fhir.r4.model.Resource;

import java.util.List;

/**
 * The outcome of evaluating a single atomic {@link Criterion} against the supplied FHIR resources and
 * hook context. It carries the three-valued result plus the evidence and a concise explanation so a
 * fired rule can explain exactly why it fired.
 *
 * @param criterionId the evaluated criterion's identifier
 * @param label       the criterion's human-readable label
 * @param truthValue  the three-valued result
 * @param evidence    the FHIR resources that matched (empty when none)
 * @param explanation a short, human-readable explanation of the decision
 */
public record CriterionEvaluationResult(
		String criterionId,
		String label,
		TruthValue truthValue,
		List<Resource> evidence,
		String explanation
) {

	public static CriterionEvaluationResult of(Criterion criterion, TruthValue truthValue, List<Resource> evidence, String explanation) {
		return new CriterionEvaluationResult(criterion.criterionId(), criterion.label(), truthValue, List.copyOf(evidence), explanation);
	}

	public boolean isTrue() {
		return truthValue == TruthValue.TRUE;
	}
}
