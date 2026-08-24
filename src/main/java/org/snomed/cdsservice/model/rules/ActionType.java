package org.snomed.cdsservice.model.rules;

/**
 * The action a rule recommends when it fires. The engine must not treat every positive rule as a
 * confirmed diagnosis: a {@code LIKELY} or {@code SUSPECTED} outcome recommends confirmation rather than
 * automatically creating a Condition.
 */
public enum ActionType {

	/** Suggest documenting a new FHIR Condition for the outcome. */
	CREATE_CONDITION,

	/** Recommend clinical confirmation before diagnosing. */
	RECOMMEND_CONFIRMATION,

	/** Recommend a specific confirmatory test (e.g. ABPM/HBPM). */
	RECOMMEND_CONFIRMATORY_TEST
}
