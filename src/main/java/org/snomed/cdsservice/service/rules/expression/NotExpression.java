package org.snomed.cdsservice.service.rules.expression;

/**
 * Logical NOT of a sub-expression.
 */
public record NotExpression(RuleExpression operand) implements RuleExpression {
}
