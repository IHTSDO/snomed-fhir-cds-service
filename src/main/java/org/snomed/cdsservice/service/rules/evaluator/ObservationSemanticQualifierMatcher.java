package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Observation;
import org.snomed.cdsservice.model.rules.Criterion;

/**
 * Strategy for deciding whether an Observation satisfies a criterion's semantic qualifiers, such as a
 * two-hour OGTT aspect, a 24-hour ABPM study or an "average" aggregation.
 * <p>
 * The exact FHIR representation of these qualifiers depends on the national profile, so this concern is
 * isolated behind an interface. An implementation must never treat the absence of a qualifier as a match:
 * when a required qualifier cannot be found or interpreted it must return {@link QualifierMatchResult#UNDETERMINED}.
 */
public interface ObservationSemanticQualifierMatcher {

	QualifierMatchResult matches(Observation observation, Criterion criterion);
}
