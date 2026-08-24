package org.snomed.cdsservice.service.rules;

import org.snomed.cdsservice.model.rules.Criterion;

import java.util.Set;

/**
 * Resolves the set of concrete codes a criterion's {@code code_selector} accepts.
 * <p>
 * For an exact {@code code} selector this is the single configured {@code system|code}, with no
 * descendant closure. For a {@code valueset}, {@code descendants} or {@code ecl} selector it is the
 * expanded membership. Implementations are expected to resolve and cache expansions once at startup so
 * that evaluation is a fast in-memory lookup; evaluation-time calls must not reach the network.
 */
public interface CodeResolver {

	/**
	 * @return the codes accepted for this criterion's main {@code code_selector}.
	 */
	Set<CodeKey> acceptableCodes(Criterion criterion);
}
