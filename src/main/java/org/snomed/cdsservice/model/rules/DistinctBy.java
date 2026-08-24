package org.snomed.cdsservice.model.rules;

/**
 * How qualifying occurrences must be distinguished from one another when {@code min_occurrences} &gt; 1.
 */
public enum DistinctBy {

	/** No distinctness constraint; any qualifying resources count. */
	NONE,

	/** Qualifying observations must fall on different calendar days. */
	CALENDAR_DAY,

	/** Qualifying observations must reference distinct Encounter resources. */
	ENCOUNTER
}
