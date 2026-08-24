package org.snomed.cdsservice.service.rules.evaluator;

import org.snomed.cdsservice.model.rules.CriterionType;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

/**
 * Dispatches criterion evaluation by {@link CriterionType}. Using a registry keeps the engine free of a
 * large type switch and lets new criterion types be added simply by supplying another evaluator.
 */
public class CriterionEvaluatorRegistry {

	private final Map<CriterionType, CriterionEvaluator> evaluatorsByType = new EnumMap<>(CriterionType.class);

	public CriterionEvaluatorRegistry(Collection<CriterionEvaluator> evaluators) {
		for (CriterionEvaluator evaluator : evaluators) {
			CriterionEvaluator existing = evaluatorsByType.put(evaluator.supportedType(), evaluator);
			if (existing != null) {
				throw new IllegalStateException("More than one evaluator registered for criterion type " + evaluator.supportedType());
			}
		}
	}

	public CriterionEvaluator get(CriterionType type) {
		CriterionEvaluator evaluator = evaluatorsByType.get(type);
		if (evaluator == null) {
			throw new IllegalStateException("No evaluator registered for criterion type " + type);
		}
		return evaluator;
	}
}
