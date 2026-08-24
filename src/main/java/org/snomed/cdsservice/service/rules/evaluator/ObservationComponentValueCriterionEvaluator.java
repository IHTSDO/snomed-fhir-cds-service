package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.CriterionType;
import org.snomed.cdsservice.service.rules.CodeKey;

/**
 * Evaluates a numeric value carried inside an {@code Observation.component[].valueQuantity}, for example
 * the systolic or diastolic component of a blood-pressure Observation.
 * <p>
 * The component value and its parent Observation stay linked: the parent code, semantic qualifiers, unit
 * and threshold are all evaluated against the same Observation instance, so a systolic reading from one
 * Observation is never combined with a diastolic reading from another.
 */
public class ObservationComponentValueCriterionEvaluator implements CriterionEvaluator {

	private final ObservationCriterionSupport support;

	public ObservationComponentValueCriterionEvaluator(ObservationCriterionSupport support) {
		this.support = support;
	}

	@Override
	public CriterionType supportedType() {
		return CriterionType.OBSERVATION_COMPONENT_VALUE;
	}

	@Override
	public CriterionEvaluationResult evaluate(Criterion criterion, RuleEvaluationContext context) {
		return support.evaluate(criterion, context, this::extractComponentQuantity);
	}

	private Quantity extractComponentQuantity(Observation observation, Criterion criterion) {
		CodeKey required = new CodeKey(criterion.componentCodeSystem(), criterion.componentCode());
		for (Observation.ObservationComponentComponent component : observation.getComponent()) {
			if (!component.hasCode() || !component.hasValueQuantity()) {
				continue;
			}
			for (Coding coding : component.getCode().getCoding()) {
				if (required.equals(CodeKey.of(coding))) {
					return component.getValueQuantity();
				}
			}
		}
		return null;
	}
}
