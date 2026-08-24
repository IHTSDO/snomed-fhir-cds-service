package org.snomed.cdsservice.service.rules;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.rules.ActionType;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.OutcomeStatus;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleEvaluationResult;
import org.snomed.cdsservice.model.rules.TruthValue;
import org.snomed.cdsservice.service.rules.evaluator.CriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.CriterionEvaluatorRegistry;
import org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport;
import org.snomed.cdsservice.service.rules.evaluator.RuleEvaluationContext;
import org.snomed.cdsservice.service.rules.expression.RuleExpressionParser;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleEngineTest {

	private final Map<String, Criterion> criteria = Map.of(
			"A", EvaluatorTestSupport.namedCriterion("A"),
			"B", EvaluatorTestSupport.namedCriterion("B"),
			"C", EvaluatorTestSupport.namedCriterion("C"));

	private RuleEvaluationResult evaluate(String expression, Map<String, TruthValue> truths) {
		RuleEngine engine = new RuleEngine(new CriterionEvaluatorRegistry(List.of(new StubEvaluator(truths))));
		return engine.evaluateRule(rule(expression), criteria, new RuleEvaluationContext(null, List.of(), Map.of()));
	}

	@Test
	void andWithOrFiresAndReportsContributingCriteria() {
		RuleEvaluationResult result = evaluate("A AND (B OR C)",
				Map.of("A", TruthValue.TRUE, "B", TruthValue.FALSE, "C", TruthValue.TRUE));
		assertEquals(TruthValue.TRUE, result.truthValue());
		assertEquals(List.of("A", "C"), contributingIds(result));
	}

	@Test
	void falseBranchDoesNotFire() {
		RuleEvaluationResult result = evaluate("A AND (B OR C)",
				Map.of("A", TruthValue.TRUE, "B", TruthValue.FALSE, "C", TruthValue.FALSE));
		assertEquals(TruthValue.FALSE, result.truthValue());
		assertTrue(result.contributingResults().isEmpty());
	}

	@Test
	void unknownPropagatesThroughAnd() {
		RuleEvaluationResult result = evaluate("A AND (B OR C)",
				Map.of("A", TruthValue.TRUE, "B", TruthValue.UNKNOWN, "C", TruthValue.FALSE));
		assertEquals(TruthValue.UNKNOWN, result.truthValue());
	}

	@Test
	void bothOrBranchesTrueContributeBoth() {
		RuleEvaluationResult result = evaluate("A OR B",
				Map.of("A", TruthValue.TRUE, "B", TruthValue.TRUE));
		assertEquals(TruthValue.TRUE, result.truthValue());
		assertEquals(List.of("A", "B"), contributingIds(result));
	}

	@Test
	void notTrueFiresWithoutContributingCriteria() {
		RuleEvaluationResult result = evaluate("NOT A", Map.of("A", TruthValue.FALSE));
		assertEquals(TruthValue.TRUE, result.truthValue());
		assertTrue(result.contributingResults().isEmpty());
	}

	private List<String> contributingIds(RuleEvaluationResult result) {
		return result.contributingResults().stream().map(CriterionEvaluationResult::criterionId).collect(Collectors.toList());
	}

	private Rule rule(String expression) {
		return new Rule("R", "uuid", "1.0.0", true, "svc", "patient-view", "path", "trigger",
				expression, RuleExpressionParser.parse(expression), OutcomeStatus.DIAGNOSTIC, ActionType.CREATE_CONDITION,
				"http://snomed.info/sct", "44054006", "Type 2 diabetes mellitus", true, CDSIndicator.info,
				"summary", "detail", "source", "url", "notes");
	}

	/** Returns the configured truth value for a criterion by its id. */
	private record StubEvaluator(Map<String, TruthValue> truths) implements CriterionEvaluator {
		@Override
		public org.snomed.cdsservice.model.rules.CriterionType supportedType() {
			return org.snomed.cdsservice.model.rules.CriterionType.OBSERVATION_VALUE;
		}

		@Override
		public CriterionEvaluationResult evaluate(Criterion criterion, RuleEvaluationContext context) {
			return CriterionEvaluationResult.of(criterion, truths.get(criterion.criterionId()), List.of(), "stub");
		}
	}
}
