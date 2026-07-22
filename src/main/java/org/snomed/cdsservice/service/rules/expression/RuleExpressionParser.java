package org.snomed.cdsservice.service.rules.expression;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive-descent parser for the constrained Boolean rule expression language.
 * <p>
 * Grammar (identifiers plus AND / OR / NOT / parentheses):
 * <pre>
 * expression       := orExpression
 * orExpression     := andExpression ("OR" andExpression)*
 * andExpression    := unaryExpression ("AND" unaryExpression)*
 * unaryExpression  := "NOT" unaryExpression | primaryExpression
 * primaryExpression:= IDENTIFIER | "(" expression ")"
 * </pre>
 * Precedence, highest first: parentheses, NOT, AND, OR. Keywords are case-insensitive. The parser never
 * evaluates the expression; it only produces an immutable {@link RuleExpression} AST.
 */
public class RuleExpressionParser {

	private enum TokenType { IDENTIFIER, AND, OR, NOT, LPAREN, RPAREN }

	private record Token(TokenType type, String text) {
	}

	private final List<Token> tokens;
	private int position;

	private RuleExpressionParser(List<Token> tokens) {
		this.tokens = tokens;
		this.position = 0;
	}

	/**
	 * Parse the supplied expression into an AST.
	 *
	 * @throws ExpressionParseException if the expression is empty, contains an unsupported token,
	 *                                  has unbalanced parentheses, or is otherwise malformed.
	 */
	public static RuleExpression parse(String expression) {
		if (expression == null || expression.isBlank()) {
			throw new ExpressionParseException("Expression is empty.");
		}
		List<Token> tokens = tokenise(expression);
		if (tokens.isEmpty()) {
			throw new ExpressionParseException("Expression is empty.");
		}
		RuleExpressionParser parser = new RuleExpressionParser(tokens);
		RuleExpression result = parser.parseExpression();
		if (!parser.isAtEnd()) {
			throw new ExpressionParseException("Unexpected token '%s' after end of expression.".formatted(parser.peek().text()));
		}
		return result;
	}

	private static List<Token> tokenise(String expression) {
		List<Token> tokens = new ArrayList<>();
		int i = 0;
		int length = expression.length();
		while (i < length) {
			char c = expression.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
			} else if (c == '(') {
				tokens.add(new Token(TokenType.LPAREN, "("));
				i++;
			} else if (c == ')') {
				tokens.add(new Token(TokenType.RPAREN, ")"));
				i++;
			} else if (isIdentifierChar(c)) {
				int start = i;
				while (i < length && isIdentifierChar(expression.charAt(i))) {
					i++;
				}
				String word = expression.substring(start, i);
				tokens.add(classifyWord(word));
			} else {
				throw new ExpressionParseException("Unsupported character '%s' in expression.".formatted(c));
			}
		}
		return tokens;
	}

	private static boolean isIdentifierChar(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}

	private static Token classifyWord(String word) {
		return switch (word.toUpperCase()) {
			case "AND" -> new Token(TokenType.AND, word);
			case "OR" -> new Token(TokenType.OR, word);
			case "NOT" -> new Token(TokenType.NOT, word);
			default -> new Token(TokenType.IDENTIFIER, word);
		};
	}

	private RuleExpression parseExpression() {
		return parseOr();
	}

	private RuleExpression parseOr() {
		RuleExpression left = parseAnd();
		while (match(TokenType.OR)) {
			RuleExpression right = parseAnd();
			left = new OrExpression(left, right);
		}
		return left;
	}

	private RuleExpression parseAnd() {
		RuleExpression left = parseUnary();
		while (match(TokenType.AND)) {
			RuleExpression right = parseUnary();
			left = new AndExpression(left, right);
		}
		return left;
	}

	private RuleExpression parseUnary() {
		if (match(TokenType.NOT)) {
			return new NotExpression(parseUnary());
		}
		return parsePrimary();
	}

	private RuleExpression parsePrimary() {
		if (isAtEnd()) {
			throw new ExpressionParseException("Expected a criterion identifier or '(' but reached the end of the expression.");
		}
		Token token = peek();
		switch (token.type()) {
			case IDENTIFIER -> {
				advance();
				return new CriterionReferenceExpression(token.text());
			}
			case LPAREN -> {
				advance();
				RuleExpression inner = parseExpression();
				if (!match(TokenType.RPAREN)) {
					throw new ExpressionParseException("Unbalanced parentheses: expected ')'.");
				}
				return inner;
			}
			default -> throw new ExpressionParseException("Unexpected token '%s'; expected a criterion identifier or '('.".formatted(token.text()));
		}
	}

	private boolean match(TokenType type) {
		if (!isAtEnd() && peek().type() == type) {
			advance();
			return true;
		}
		return false;
	}

	private Token peek() {
		return tokens.get(position);
	}

	private void advance() {
		position++;
	}

	private boolean isAtEnd() {
		return position >= tokens.size();
	}
}
