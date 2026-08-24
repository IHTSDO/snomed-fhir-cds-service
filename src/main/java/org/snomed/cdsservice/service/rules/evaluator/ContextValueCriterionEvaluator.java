package org.snomed.cdsservice.service.rules.evaluator;

import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.CriterionType;
import org.snomed.cdsservice.model.rules.TruthValue;

import java.util.List;

/**
 * Evaluates an explicit value supplied by the CDS client in the CDS Hooks request context.
 * <p>
 * A missing context key yields {@link TruthValue#UNKNOWN}, never FALSE: absence means the client did not
 * supply the information, not that the condition is false. This is how the hypertension pathway records
 * that ABPM and HBPM are unavailable or impractical — it must be stated, not inferred.
 */
public class ContextValueCriterionEvaluator implements CriterionEvaluator {

	@Override
	public CriterionType supportedType() {
		return CriterionType.CONTEXT_VALUE;
	}

	@Override
	public CriterionEvaluationResult evaluate(Criterion criterion, RuleEvaluationContext context) {
		Object value = context.hookContext().get(criterion.contextKey());
		if (value == null) {
			return CriterionEvaluationResult.of(criterion, TruthValue.UNKNOWN, List.of(),
					"Context value '%s' was not supplied.".formatted(criterion.contextKey()));
		}
		boolean matches = String.valueOf(value).trim().equalsIgnoreCase(criterion.contextValue().trim());
		TruthValue truthValue = matches ? TruthValue.TRUE : TruthValue.FALSE;
		return CriterionEvaluationResult.of(criterion, truthValue, List.of(),
				"Context '%s' = '%s' (expected '%s').".formatted(criterion.contextKey(), value, criterion.contextValue()));
	}
}
