package org.snomed.cdsservice.service.rules.expression;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuleExpressionParserTest {

	@Test
	void singleIdentifier() {
		RuleExpression expression = RuleExpressionParser.parse("A");
		CriterionReferenceExpression reference = assertInstanceOf(CriterionReferenceExpression.class, expression);
		assertEquals("A", reference.criterionId());
	}

	@Test
	void andExpression() {
		AndExpression and = assertInstanceOf(AndExpression.class, RuleExpressionParser.parse("A AND B"));
		assertEquals(new CriterionReferenceExpression("A"), and.left());
		assertEquals(new CriterionReferenceExpression("B"), and.right());
	}

	@Test
	void orExpression() {
		OrExpression or = assertInstanceOf(OrExpression.class, RuleExpressionParser.parse("A OR B"));
		assertEquals(new CriterionReferenceExpression("A"), or.left());
		assertEquals(new CriterionReferenceExpression("B"), or.right());
	}

	@Test
	void notExpression() {
		NotExpression not = assertInstanceOf(NotExpression.class, RuleExpressionParser.parse("NOT A"));
		assertEquals(new CriterionReferenceExpression("A"), not.operand());
	}

	@Test
	void andBindsTighterThanOr() {
		// A OR B AND C must parse as A OR (B AND C), not (A OR B) AND C.
		RuleExpression expression = RuleExpressionParser.parse("A OR B AND C");
		OrExpression or = assertInstanceOf(OrExpression.class, expression);
		assertEquals(new CriterionReferenceExpression("A"), or.left());
		AndExpression and = assertInstanceOf(AndExpression.class, or.right());
		assertEquals(new CriterionReferenceExpression("B"), and.left());
		assertEquals(new CriterionReferenceExpression("C"), and.right());
	}

	@Test
	void notBindsTighterThanAnd() {
		// NOT A AND B must parse as (NOT A) AND B.
		AndExpression and = assertInstanceOf(AndExpression.class, RuleExpressionParser.parse("NOT A AND B"));
		assertInstanceOf(NotExpression.class, and.left());
		assertEquals(new CriterionReferenceExpression("B"), and.right());
	}

	@Test
	void parenthesesOverridePrecedence() {
		// (A OR B) AND C
		AndExpression and = assertInstanceOf(AndExpression.class, RuleExpressionParser.parse("(A OR B) AND C"));
		assertInstanceOf(OrExpression.class, and.left());
		assertEquals(new CriterionReferenceExpression("C"), and.right());
	}

	@Test
	void nestedParentheses() {
		// Mirrors the diabetes OGTT rule shape.
		RuleExpression expression = RuleExpressionParser.parse("A OR (B AND (C OR D))");
		OrExpression outer = assertInstanceOf(OrExpression.class, expression);
		AndExpression and = assertInstanceOf(AndExpression.class, outer.right());
		assertInstanceOf(OrExpression.class, and.right());
	}

	@Test
	void keywordsAreCaseInsensitive() {
		RuleExpression lower = RuleExpressionParser.parse("A and (B or C)");
		RuleExpression upper = RuleExpressionParser.parse("A AND (B OR C)");
		assertEquals(upper, lower);
	}

	@Test
	void extraWhitespaceIsIgnored() {
		assertEquals(
				RuleExpressionParser.parse("A AND B"),
				RuleExpressionParser.parse("   A\tAND    B  "));
	}

	@Test
	void referencedCriterionIdsAreCollected() {
		RuleExpression expression = RuleExpressionParser.parse("DM_A AND (DM_B OR NOT DM_C)");
		assertEquals(Set.of("DM_A", "DM_B", "DM_C"), expression.referencedCriterionIds());
	}

	@Test
	void emptyExpressionIsRejected() {
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse(""));
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse("   "));
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse(null));
	}

	@Test
	void unbalancedParenthesesAreRejected() {
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse("(A AND B"));
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse("A AND B)"));
	}

	@Test
	void danglingOperatorIsRejected() {
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse("A AND"));
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse("OR A"));
	}

	@Test
	void unsupportedCharacterIsRejected() {
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse("A && B"));
	}

	@Test
	void adjacentIdentifiersAreRejected() {
		assertThrows(ExpressionParseException.class, () -> RuleExpressionParser.parse("A B"));
	}
}
