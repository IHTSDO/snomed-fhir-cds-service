package org.snomed.cdsservice.model.rules;

/**
 * The clinical certainty a rule expresses when it fires. These states are distinct: a {@code SUSPECTED}
 * or {@code LIKELY} result must not be treated as a confirmed diagnosis.
 */
public enum OutcomeStatus {

	SUSPECTED,
	LIKELY,
	DIAGNOSTIC
}
