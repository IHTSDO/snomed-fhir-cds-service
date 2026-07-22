package org.snomed.cdsservice.model.rules;

/**
 * Comparison operators supported by the diagnostic criteria in this first version.
 */
public enum CriterionOperator {

	/** actual &gt;= value_low. */
	GE,

	/** actual &gt; value_low. */
	GT,

	/** actual &lt;= value_low. */
	LE,

	/** actual &lt; value_low. */
	LT,

	/** actual == value_low (or, for a context criterion, the contextual value equals context_value). */
	EQ,

	/** value_low (bound by lower_inclusive) .. value_high (bound by upper_inclusive). */
	BETWEEN,

	/** At least min_occurrences matching resources exist. */
	EXISTS,

	/** No matching resource is present in the supplied data. */
	NOT_EXISTS;

	/**
	 * @return true when the operator compares a numeric value against value_low / value_high.
	 */
	public boolean isNumericComparison() {
		return this == GE || this == GT || this == LE || this == LT || this == EQ || this == BETWEEN;
	}
}
