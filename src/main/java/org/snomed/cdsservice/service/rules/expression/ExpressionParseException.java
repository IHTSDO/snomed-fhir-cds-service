package org.snomed.cdsservice.service.rules.expression;

/**
 * Thrown when a rule expression cannot be tokenised or parsed. The message describes the specific
 * problem (unexpected token, unbalanced parentheses, empty expression, etc.) so that the loader can
 * report it with the offending file and row.
 */
public class ExpressionParseException extends RuntimeException {

	public ExpressionParseException(String message) {
		super(message);
	}
}
