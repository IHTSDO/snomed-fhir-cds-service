package org.snomed.cdsservice.model.rules;

/**
 * How a candidate coding is compared against a criterion's {@code code_selector}.
 */
public enum CodeMatchType {

	/** Exact {@code system + code} equality. */
	EXACT,

	/** Membership in an expanded canonical value set. */
	VALUESET
}
