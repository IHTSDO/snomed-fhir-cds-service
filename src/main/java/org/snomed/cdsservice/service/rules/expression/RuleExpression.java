package org.snomed.cdsservice.service.rules.expression;

import java.util.Set;
import java.util.TreeSet;

/**
 * Abstract syntax tree for the constrained, CQL-inspired Boolean rule expression language.
 * <p>
 * This AST is deliberately independent from the original TSV expression string so that a future CQL
 * generator or execution engine can be added without changing the TSV model. The language supports only
 * criterion identifiers combined with {@code AND}, {@code OR}, {@code NOT} and parentheses.
 */
public sealed interface RuleExpression
		permits CriterionReferenceExpression, AndExpression, OrExpression, NotExpression {

	/**
	 * @return the identifiers of every criterion referenced anywhere in this expression tree.
	 */
	default Set<String> referencedCriterionIds() {
		Set<String> ids = new TreeSet<>();
		collectCriterionIds(this, ids);
		return ids;
	}

	private static void collectCriterionIds(RuleExpression expression, Set<String> ids) {
		if (expression instanceof CriterionReferenceExpression reference) {
			ids.add(reference.criterionId());
		} else if (expression instanceof NotExpression not) {
			collectCriterionIds(not.operand(), ids);
		} else if (expression instanceof AndExpression and) {
			collectCriterionIds(and.left(), ids);
			collectCriterionIds(and.right(), ids);
		} else if (expression instanceof OrExpression or) {
			collectCriterionIds(or.left(), ids);
			collectCriterionIds(or.right(), ids);
		}
	}
}
