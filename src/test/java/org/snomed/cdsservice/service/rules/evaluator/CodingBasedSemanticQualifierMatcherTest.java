package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Observation;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.rules.Criterion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.UCUM;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observation;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observationValue;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observationValueWithTimeAspect;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.withCoding;

class CodingBasedSemanticQualifierMatcherTest {

	private static final String OGTT = "113076002";
	private static final String TWO_HOUR = "123030002";

	private final CodingBasedSemanticQualifierMatcher matcher = new CodingBasedSemanticQualifierMatcher();

	@Test
	void noQualifierDeclaredIsNotApplicable() {
		Criterion criterion = observationValue(OGTT, org.snomed.cdsservice.model.rules.CriterionOperator.GE, "11.1", "mmol/L", 1, org.snomed.cdsservice.model.rules.DistinctBy.NONE);
		Observation observation = observation(OGTT, Observation.ObservationStatus.FINAL, "12.0", UCUM, "mmol/L", "2024-05-01");
		assertEquals(QualifierMatchResult.NOT_APPLICABLE, matcher.matches(observation, criterion));
	}

	@Test
	void declaredQualifierPresentIsMatched() {
		Criterion criterion = observationValueWithTimeAspect(OGTT, TWO_HOUR);
		Observation observation = observation(OGTT, Observation.ObservationStatus.FINAL, "12.0", UCUM, "mmol/L", "2024-05-01");
		// The two-hour aspect concept is carried as an additional coding on Observation.code.
		withCoding(observation, EvaluatorTestSupport.SNOMED, TWO_HOUR);
		assertEquals(QualifierMatchResult.MATCHED, matcher.matches(observation, criterion));
	}

	@Test
	void declaredQualifierAbsentIsUndetermined() {
		Criterion criterion = observationValueWithTimeAspect(OGTT, TWO_HOUR);
		Observation observation = observation(OGTT, Observation.ObservationStatus.FINAL, "12.0", UCUM, "mmol/L", "2024-05-01");
		assertEquals(QualifierMatchResult.UNDETERMINED, matcher.matches(observation, criterion));
	}
}
