package org.snomed.cdsservice.model.rules;

import java.util.List;
import java.util.Map;

/**
 * The criteria and rules loaded for one diagnostic domain (for example diabetes or hypertension).
 *
 * @param domain    a human-readable name for logging and error messages
 * @param criteria  atomic criteria keyed by {@code criterion_id}
 * @param rules     rules in file order, including those with {@code enabled = false}
 */
public record RuleSet(
		String domain,
		Map<String, Criterion> criteria,
		List<Rule> rules
) {

	/**
	 * @return only the enabled rules, preserving file order.
	 */
	public List<Rule> enabledRules() {
		return rules.stream().filter(Rule::enabled).toList();
	}
}
