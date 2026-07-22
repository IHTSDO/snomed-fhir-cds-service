package org.snomed.cdsservice.service.rules.evaluator;

/**
 * The result of checking a semantic qualifier (time aspect, aggregation, etc.) on an Observation.
 */
public enum QualifierMatchResult {

	/** The criterion declares no semantic qualifier, so none needs to match. */
	NOT_APPLICABLE,

	/** Every declared qualifier was found on the Observation. */
	MATCHED,

	/** A declared qualifier could not be found or interpreted; the match is undetermined. */
	UNDETERMINED
}
