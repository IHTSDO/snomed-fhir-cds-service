package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.CriterionOperator;
import org.snomed.cdsservice.model.rules.DistinctBy;
import org.snomed.cdsservice.model.rules.TruthValue;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.UCUM;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observation;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observationSupport;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observationValue;

class ObservationValueCriterionEvaluatorTest {

	private static final String FBG = "271062006";
	private final ObservationValueCriterionEvaluator evaluator = new ObservationValueCriterionEvaluator(observationSupport());

	private TruthValue evaluate(Criterion criterion, Resource... resources) {
		return evaluator.evaluate(criterion, context(resources)).truthValue();
	}

	private RuleEvaluationContext context(Resource... resources) {
		return new RuleEvaluationContext(null, List.of(resources), Map.of());
	}

	@Test
	void aboveThresholdIsTrue() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 1, DistinctBy.NONE);
		assertEquals(TruthValue.TRUE, evaluate(criterion, observation(FBG, Observation.ObservationStatus.FINAL, "7.4", UCUM, "mmol/L", "2024-05-01")));
	}

	@Test
	void exactThresholdBoundaryIsTrue() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 1, DistinctBy.NONE);
		assertEquals(TruthValue.TRUE, evaluate(criterion, observation(FBG, Observation.ObservationStatus.FINAL, "7.0", UCUM, "mmol/L", "2024-05-01")));
	}

	@Test
	void belowThresholdIsFalse() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 1, DistinctBy.NONE);
		assertEquals(TruthValue.FALSE, evaluate(criterion, observation(FBG, Observation.ObservationStatus.FINAL, "6.5", UCUM, "mmol/L", "2024-05-01")));
	}

	@Test
	void wrongCodeIsUnknown() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 1, DistinctBy.NONE);
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, observation("999999", Observation.ObservationStatus.FINAL, "9.0", UCUM, "mmol/L", "2024-05-01")));
	}

	@Test
	void unacceptedStatusDoesNotSatisfy() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 1, DistinctBy.NONE);
		// A preliminary observation matching the code cannot satisfy the criterion; there is no final data to decide on.
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, observation(FBG, Observation.ObservationStatus.PRELIMINARY, "9.0", UCUM, "mmol/L", "2024-05-01")));
	}

	@Test
	void incompatibleUnitIsUnknown() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 1, DistinctBy.NONE);
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, observation(FBG, Observation.ObservationStatus.FINAL, "130", UCUM, "mg/dL", "2024-05-01")));
	}

	@Test
	void missingUnitSystemIsUnknown() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 1, DistinctBy.NONE);
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, observation(FBG, Observation.ObservationStatus.FINAL, "9.0", null, "mmol/L", "2024-05-01")));
	}

	@Test
	void twoOccurrencesSameDayIsFalse() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 2, DistinctBy.CALENDAR_DAY);
		assertEquals(TruthValue.FALSE, evaluate(criterion,
				observation(FBG, Observation.ObservationStatus.FINAL, "9.0", UCUM, "mmol/L", "2024-05-01"),
				observation(FBG, Observation.ObservationStatus.FINAL, "9.5", UCUM, "mmol/L", "2024-05-01")));
	}

	@Test
	void twoOccurrencesDifferentDaysIsTrue() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 2, DistinctBy.CALENDAR_DAY);
		assertEquals(TruthValue.TRUE, evaluate(criterion,
				observation(FBG, Observation.ObservationStatus.FINAL, "9.0", UCUM, "mmol/L", "2024-05-01"),
				observation(FBG, Observation.ObservationStatus.FINAL, "9.5", UCUM, "mmol/L", "2024-05-02")));
	}

	@Test
	void undeterminableDistinctnessIsUnknown() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 2, DistinctBy.CALENDAR_DAY);
		// One dated and one undated qualifying observation: the second occasion cannot be confirmed distinct.
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion,
				observation(FBG, Observation.ObservationStatus.FINAL, "9.0", UCUM, "mmol/L", "2024-05-01"),
				observation(FBG, Observation.ObservationStatus.FINAL, "9.5", UCUM, "mmol/L", null)));
	}

	@Test
	void evidenceIsPreservedOnTrue() {
		Criterion criterion = observationValue(FBG, CriterionOperator.GE, "7.0", "mmol/L", 1, DistinctBy.NONE);
		CriterionEvaluationResult result = evaluator.evaluate(criterion,
				context(observation(FBG, Observation.ObservationStatus.FINAL, "7.4", UCUM, "mmol/L", "2024-05-01")));
		assertEquals(TruthValue.TRUE, result.truthValue());
		assertEquals(1, result.evidence().size());
	}
}
