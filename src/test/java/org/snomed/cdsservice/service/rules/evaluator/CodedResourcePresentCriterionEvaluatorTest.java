package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionOperator;
import org.snomed.cdsservice.model.rules.TruthValue;
import org.snomed.cdsservice.service.rules.CodeKey;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.SNOMED;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.codedResourcePresent;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.condition;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.resolverReturning;

class CodedResourcePresentCriterionEvaluatorTest {

	private static final String SYMPTOM = "62315008"; // arbitrary SNOMED symptom code for the test value set

	private final CodedResourcePresentCriterionEvaluator evaluator =
			new CodedResourcePresentCriterionEvaluator(resolverReturning(new CodeKey(SNOMED, SYMPTOM)));

	private TruthValue evaluate(Criterion criterion, Resource... resources) {
		return evaluator.evaluate(criterion, new RuleEvaluationContext(null, List.of(resources), Map.of())).truthValue();
	}

	@Test
	void activeMatchingConditionExistsIsTrue() {
		Criterion criterion = codedResourcePresent("Condition", CriterionOperator.EXISTS);
		assertEquals(TruthValue.TRUE, evaluate(criterion, condition(SYMPTOM, "active", "confirmed")));
	}

	@Test
	void noConditionIsUnknown() {
		Criterion criterion = codedResourcePresent("Condition", CriterionOperator.EXISTS);
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion));
	}

	@Test
	void wrongCodeIsUnknown() {
		Criterion criterion = codedResourcePresent("Condition", CriterionOperator.EXISTS);
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, condition("111111", "active", "confirmed")));
	}

	@Test
	void enteredInErrorConditionDoesNotSatisfy() {
		Criterion criterion = codedResourcePresent("Condition", CriterionOperator.EXISTS);
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, condition(SYMPTOM, "active", "entered-in-error")));
	}

	@Test
	void inactiveConditionDoesNotSatisfy() {
		Criterion criterion = codedResourcePresent("Condition", CriterionOperator.EXISTS);
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, condition(SYMPTOM, "resolved", "confirmed")));
	}

	@Test
	void notExistsWithMatchingConditionIsFalse() {
		Criterion criterion = codedResourcePresent("Condition", CriterionOperator.NOT_EXISTS);
		assertEquals(TruthValue.FALSE, evaluate(criterion, condition(SYMPTOM, "active", "confirmed")));
	}

	@Test
	void notExistsWithNoConditionIsUnknown() {
		Criterion criterion = codedResourcePresent("Condition", CriterionOperator.NOT_EXISTS);
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion));
	}
}
