package org.snomed.cdsservice.service.rules.evaluator;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.TruthValue;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.contextValue;

class ContextValueCriterionEvaluatorTest {

	private static final String KEY = "abpmHbpUnavailableOrImpractical";
	private final ContextValueCriterionEvaluator evaluator = new ContextValueCriterionEvaluator();

	private TruthValue evaluate(Map<String, Object> hookContext) {
		Criterion criterion = contextValue(KEY, "true");
		return evaluator.evaluate(criterion, new RuleEvaluationContext(null, List.of(), hookContext)).truthValue();
	}

	@Test
	void matchingContextValueIsTrue() {
		assertEquals(TruthValue.TRUE, evaluate(Map.of(KEY, true)));
		assertEquals(TruthValue.TRUE, evaluate(Map.of(KEY, "true")));
	}

	@Test
	void nonMatchingContextValueIsFalse() {
		assertEquals(TruthValue.FALSE, evaluate(Map.of(KEY, false)));
	}

	@Test
	void missingContextValueIsUnknown() {
		assertEquals(TruthValue.UNKNOWN, evaluate(Map.of()));
	}
}
