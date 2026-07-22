package org.snomed.cdsservice.service.rules.expression;

/**
 * Logical OR of two sub-expressions.
 */
public record OrExpression(RuleExpression left, RuleExpression right) implements RuleExpression {
}
