package org.snomed.cdsservice.service.rules.expression;

/**
 * A leaf referencing an atomic criterion by its identifier.
 */
public record CriterionReferenceExpression(String criterionId) implements RuleExpression {
}
