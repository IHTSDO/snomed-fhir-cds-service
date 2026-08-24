package org.snomed.cdsservice.model.rules;

/**
 * The kind of clinical fact an atomic {@link Criterion} evaluates.
 * Only the types present in the supplied demonstration TSV files are supported in this first version.
 */
public enum CriterionType {

	/** A numeric value carried directly on {@code Observation.valueQuantity}. */
	OBSERVATION_VALUE,

	/** A numeric value carried inside an {@code Observation.component[].valueQuantity}. */
	OBSERVATION_COMPONENT_VALUE,

	/** Presence of a coded FHIR resource (e.g. an active {@code Condition}) matching a code or value set. */
	CODED_RESOURCE_PRESENT,

	/** An explicit value supplied by the CDS client in the CDS Hooks request context. */
	CONTEXT_VALUE
}
