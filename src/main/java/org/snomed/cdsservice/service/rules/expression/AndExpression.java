package org.snomed.cdsservice.service.rules.expression;

/**
 * Logical AND of two sub-expressions.
 */
public record AndExpression(RuleExpression left, RuleExpression right) implements RuleExpression {
}
