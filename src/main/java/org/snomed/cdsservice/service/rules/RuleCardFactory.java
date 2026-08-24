package org.snomed.cdsservice.service.rules;

import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleEvaluationResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds a fresh {@link CDSCard} for a fired rule, resolving the card template placeholders. A new card
 * instance is created per request so no mutable state is shared between requests or clients.
 * <p>
 * Supported placeholders:
 * <ul>
 *     <li>{@code {{MatchedCriteria}}} — the labels of the criteria that contributed to the successful path</li>
 *     <li>{@code {{OutcomeDisplay}}} — the rule's configured outcome display</li>
 * </ul>
 */
public class RuleCardFactory {

	public CDSCard createCard(RuleEvaluationResult result) {
		Rule rule = result.rule();

		Map<String, String> placeholders = new LinkedHashMap<>();
		placeholders.put("MatchedCriteria", matchedCriteria(result));
		placeholders.put("OutcomeDisplay", nullToEmpty(rule.outcomeDisplay()));

		String summary = applyPlaceholders(rule.cardSummary(), placeholders);
		String detail = applyPlaceholders(rule.cardDetail(), placeholders);
		CDSSource source = new CDSSource(rule.sourceLabel(), rule.sourceUrl());

		return new CDSCard(rule.cardUuid(), summary, detail, rule.cardIndicator(), source, null, null, alertType(rule));
	}

	private String matchedCriteria(RuleEvaluationResult result) {
		return result.contributingResults().stream()
				.map(CriterionEvaluationResult::label)
				.collect(Collectors.joining(", "));
	}

	private String applyPlaceholders(String template, Map<String, String> placeholders) {
		if (template == null) {
			return null;
		}
		String rendered = template;
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
		}
		return rendered;
	}

	private String alertType(Rule rule) {
		String name = rule.outcomeStatus().name().toLowerCase();
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
