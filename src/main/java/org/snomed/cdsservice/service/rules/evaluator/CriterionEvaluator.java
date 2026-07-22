package org.snomed.cdsservice.service.rules.evaluator;

import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.CriterionType;

/**
 * Evaluates one kind of atomic criterion against the FHIR resources and context of a request. Each
 * implementation handles a single {@link CriterionType}; the registry dispatches by type.
 */
public interface CriterionEvaluator {

	CriterionType supportedType();

	CriterionEvaluationResult evaluate(Criterion criterion, RuleEvaluationContext context);
}
