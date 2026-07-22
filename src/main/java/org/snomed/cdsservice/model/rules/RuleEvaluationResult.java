package org.snomed.cdsservice.model.rules;

import java.util.List;

/**
 * The outcome of evaluating one {@link Rule}: the overall three-valued result, the result of every
 * criterion that was evaluated, and the subset of criteria that materially contributed to a successful
 * (TRUE) evaluation path.
 *
 * @param rule                the evaluated rule
 * @param truthValue          the overall three-valued result of the rule's Boolean expression
 * @param criterionResults    the result of every distinct criterion evaluated for this rule
 * @param contributingResults the TRUE criteria on the path that made the rule fire (empty unless TRUE)
 */
public record RuleEvaluationResult(
		Rule rule,
		TruthValue truthValue,
		List<CriterionEvaluationResult> criterionResults,
		List<CriterionEvaluationResult> contributingResults
) {

	public boolean fired() {
		return truthValue == TruthValue.TRUE;
	}
}
