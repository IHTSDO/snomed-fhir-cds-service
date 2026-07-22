package org.snomed.cdsservice.model.rules;

/**
 * What a criterion returns when the data required to decide it is missing.
 * <p>
 * Only {@link #UNKNOWN} is used by the supplied demonstration files. The enum exists so that alternative
 * behaviours (for example treating missing data as {@code FALSE} for a specific criterion) can be added
 * later without changing the TSV model.
 */
public enum MissingDataBehavior {

	/** Missing data yields {@link TruthValue#UNKNOWN}. */
	UNKNOWN
}
