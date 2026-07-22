package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionOperator;
import org.snomed.cdsservice.model.rules.DistinctBy;
import org.snomed.cdsservice.model.rules.TruthValue;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.addComponent;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.bpObservation;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observationComponentValue;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observationSupport;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.withEncounter;

class ObservationComponentValueCriterionEvaluatorTest {

	private static final String CLINIC_BP = "75367002";
	private static final String SYSTOLIC = "271649006";
	private static final String DIASTOLIC = "271650006";

	private final ObservationComponentValueCriterionEvaluator evaluator =
			new ObservationComponentValueCriterionEvaluator(observationSupport());

	private TruthValue evaluate(Criterion criterion, Resource... resources) {
		return evaluator.evaluate(criterion, new RuleEvaluationContext(null, List.of(resources), Map.of())).truthValue();
	}

	@Test
	void systolicWithinSuspectedRangeIsTrue() {
		Criterion criterion = observationComponentValue(CLINIC_BP, SYSTOLIC, CriterionOperator.BETWEEN, "140", "180");
		Observation observation = addComponent(addComponent(bpObservation(CLINIC_BP, "2024-05-01"), SYSTOLIC, "150"), DIASTOLIC, "60");
		assertEquals(TruthValue.TRUE, evaluate(criterion, observation));
	}

	@Test
	void exclusiveUpperBoundKeepsSevereOutside() {
		Criterion criterion = observationComponentValue(CLINIC_BP, SYSTOLIC, CriterionOperator.BETWEEN, "140", "180");
		// 180 is excluded so severe hypertension does not fall into the suspected range.
		Observation observation = addComponent(bpObservation(CLINIC_BP, "2024-05-01"), SYSTOLIC, "180");
		assertEquals(TruthValue.FALSE, evaluate(criterion, observation));
	}

	@Test
	void diastolicComponentIsReadFromTheSameObservation() {
		// The systolic criterion must read the systolic component (60 -> below range) and not the diastolic (150).
		Criterion criterion = observationComponentValue(CLINIC_BP, SYSTOLIC, CriterionOperator.BETWEEN, "140", "180");
		Observation observation = addComponent(addComponent(bpObservation(CLINIC_BP, "2024-05-01"), SYSTOLIC, "60"), DIASTOLIC, "150");
		assertEquals(TruthValue.FALSE, evaluate(criterion, observation));
	}

	@Test
	void missingComponentIsUnknown() {
		Criterion criterion = observationComponentValue(CLINIC_BP, SYSTOLIC, CriterionOperator.BETWEEN, "140", "180");
		Observation observation = addComponent(bpObservation(CLINIC_BP, "2024-05-01"), DIASTOLIC, "100");
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, observation));
	}

	@Test
	void ambulatoryAverageAboveThresholdIsTrue() {
		Criterion criterion = observationComponentValue(CLINIC_BP, SYSTOLIC, CriterionOperator.GE, "130", null);
		Observation observation = addComponent(bpObservation(CLINIC_BP, "2024-05-01"), SYSTOLIC, "132");
		assertEquals(TruthValue.TRUE, evaluate(criterion, observation));
	}

	@Test
	void twoDistinctEncountersIsTrue() {
		Criterion criterion = observationComponentValue(CLINIC_BP, SYSTOLIC, CriterionOperator.GE, "140", null, 2, DistinctBy.ENCOUNTER);
		Observation visit1 = withEncounter(addComponent(bpObservation(CLINIC_BP, "2024-05-01"), SYSTOLIC, "150"), "Encounter/1");
		Observation visit2 = withEncounter(addComponent(bpObservation(CLINIC_BP, "2024-06-01"), SYSTOLIC, "148"), "Encounter/2");
		assertEquals(TruthValue.TRUE, evaluate(criterion, visit1, visit2));
	}

	@Test
	void twoReadingsSameEncounterIsFalse() {
		Criterion criterion = observationComponentValue(CLINIC_BP, SYSTOLIC, CriterionOperator.GE, "140", null, 2, DistinctBy.ENCOUNTER);
		Observation reading1 = withEncounter(addComponent(bpObservation(CLINIC_BP, "2024-05-01"), SYSTOLIC, "150"), "Encounter/1");
		Observation reading2 = withEncounter(addComponent(bpObservation(CLINIC_BP, "2024-05-01"), SYSTOLIC, "148"), "Encounter/1");
		assertEquals(TruthValue.FALSE, evaluate(criterion, reading1, reading2));
	}

	@Test
	void readingsWithoutEncounterAreUnknown() {
		Criterion criterion = observationComponentValue(CLINIC_BP, SYSTOLIC, CriterionOperator.GE, "140", null, 2, DistinctBy.ENCOUNTER);
		Observation reading1 = addComponent(bpObservation(CLINIC_BP, "2024-05-01"), SYSTOLIC, "150");
		Observation reading2 = addComponent(bpObservation(CLINIC_BP, "2024-06-01"), SYSTOLIC, "148");
		assertEquals(TruthValue.UNKNOWN, evaluate(criterion, reading1, reading2));
	}
}
