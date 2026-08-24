package org.snomed.cdsservice.service.rules;

import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleEvaluationResult;
import org.snomed.cdsservice.model.rules.TruthValue;
import org.snomed.cdsservice.service.rules.evaluator.CriterionEvaluatorRegistry;
import org.snomed.cdsservice.service.rules.evaluator.RuleEvaluationContext;
import org.snomed.cdsservice.service.rules.expression.AndExpression;
import org.snomed.cdsservice.service.rules.expression.CriterionReferenceExpression;
import org.snomed.cdsservice.service.rules.expression.NotExpression;
import org.snomed.cdsservice.service.rules.expression.OrExpression;
import org.snomed.cdsservice.service.rules.expression.RuleExpression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates a rule's Boolean expression against a request using three-valued logic and records the
 * evidence. Each referenced criterion is evaluated exactly once; the engine then folds the results
 * through the AST and, when the rule fires, works out which TRUE criteria actually lay on the successful
 * path (for the {@code {{MatchedCriteria}}} card placeholder).
 */
public class RuleEngine {

	private final CriterionEvaluatorRegistry evaluatorRegistry;

	public RuleEngine(CriterionEvaluatorRegistry evaluatorRegistry) {
		this.evaluatorRegistry = evaluatorRegistry;
	}

	public RuleEvaluationResult evaluateRule(Rule rule, Map<String, Criterion> criteria, RuleEvaluationContext context) {
		Map<String, CriterionEvaluationResult> resultsById = new LinkedHashMap<>();
		for (String criterionId : rule.expression().referencedCriterionIds()) {
			Criterion criterion = criteria.get(criterionId);
			if (criterion == null) {
				throw new IllegalStateException("Rule '%s' references criterion '%s' that is not in the rule set.".formatted(rule.ruleId(), criterionId));
			}
			CriterionEvaluationResult result = evaluatorRegistry.get(criterion.criterionType()).evaluate(criterion, context);
			resultsById.put(criterionId, result);
		}

		TruthValue truthValue = evaluate(rule.expression(), resultsById);
		List<CriterionEvaluationResult> criterionResults = new ArrayList<>(resultsById.values());

		List<CriterionEvaluationResult> contributing = new ArrayList<>();
		if (truthValue == TruthValue.TRUE) {
			for (String criterionId : contributingCriterionIds(rule.expression(), resultsById)) {
				contributing.add(resultsById.get(criterionId));
			}
		}
		return new RuleEvaluationResult(rule, truthValue, criterionResults, contributing);
	}

	private TruthValue evaluate(RuleExpression expression, Map<String, CriterionEvaluationResult> results) {
		if (expression instanceof CriterionReferenceExpression reference) {
			return results.get(reference.criterionId()).truthValue();
		}
		if (expression instanceof NotExpression not) {
			return evaluate(not.operand(), results).negate();
		}
		if (expression instanceof AndExpression and) {
			return evaluate(and.left(), results).and(evaluate(and.right(), results));
		}
		if (expression instanceof OrExpression or) {
			return evaluate(or.left(), results).or(evaluate(or.right(), results));
		}
		throw new IllegalStateException("Unsupported expression node: " + expression);
	}

	private Set<String> contributingCriterionIds(RuleExpression expression, Map<String, CriterionEvaluationResult> results) {
		Set<String> contributing = new LinkedHashSet<>();
		if (expression instanceof CriterionReferenceExpression reference) {
			if (results.get(reference.criterionId()).truthValue() == TruthValue.TRUE) {
				contributing.add(reference.criterionId());
			}
		} else if (expression instanceof AndExpression and) {
			if (evaluate(and, results) == TruthValue.TRUE) {
				contributing.addAll(contributingCriterionIds(and.left(), results));
				contributing.addAll(contributingCriterionIds(and.right(), results));
			}
		} else if (expression instanceof OrExpression or) {
			if (evaluate(or.left(), results) == TruthValue.TRUE) {
				contributing.addAll(contributingCriterionIds(or.left(), results));
			}
			if (evaluate(or.right(), results) == TruthValue.TRUE) {
				contributing.addAll(contributingCriterionIds(or.right(), results));
			}
		}
		// A NOT contributes no TRUE criterion to the matched-criteria list.
		return contributing;
	}
}
