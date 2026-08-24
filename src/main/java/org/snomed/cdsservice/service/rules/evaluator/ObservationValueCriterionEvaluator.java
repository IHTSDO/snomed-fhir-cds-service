package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.CriterionType;

/**
 * Evaluates a numeric value carried directly on {@code Observation.valueQuantity}.
 */
public class ObservationValueCriterionEvaluator implements CriterionEvaluator {

	private final ObservationCriterionSupport support;

	public ObservationValueCriterionEvaluator(ObservationCriterionSupport support) {
		this.support = support;
	}

	@Override
	public CriterionType supportedType() {
		return CriterionType.OBSERVATION_VALUE;
	}

	@Override
	public CriterionEvaluationResult evaluate(Criterion criterion, RuleEvaluationContext context) {
		return support.evaluate(criterion, context, this::extractValueQuantity);
	}

	private Quantity extractValueQuantity(Observation observation, Criterion criterion) {
		return observation.hasValueQuantity() ? observation.getValueQuantity() : null;
	}
}
