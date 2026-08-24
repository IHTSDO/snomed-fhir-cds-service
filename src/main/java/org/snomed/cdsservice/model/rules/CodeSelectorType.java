package org.snomed.cdsservice.model.rules;

/**
 * How the {@code code_selector} of a criterion should be interpreted.
 * <p>
 * The first version supports exact code matching and canonical value set expansion. The {@code DESCENDANTS}
 * and {@code ECL} modes are declared so the model can grow without redesign, but they are not yet evaluated.
 */
public enum CodeSelectorType {

	/** A single {@code system|code} matched exactly (no descendant closure). */
	CODE,

	/** A canonical FHIR ValueSet URL expanded through the terminology server. */
	VALUESET,

	/** Reserved for a future SNOMED CT descendant-closure selector. */
	DESCENDANTS,

	/** Reserved for a future SNOMED CT ECL selector. */
	ECL
}
