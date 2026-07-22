package org.snomed.cdsservice.service.rules;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.rules.CodeMatchType;
import org.snomed.cdsservice.model.rules.CodeSelectorType;
import org.snomed.cdsservice.model.rules.CriterionOperator;
import org.snomed.cdsservice.model.rules.CriterionType;
import org.snomed.cdsservice.model.rules.ActionType;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleSet;
import org.snomed.cdsservice.model.rules.DistinctBy;
import org.snomed.cdsservice.model.rules.MissingDataBehavior;
import org.snomed.cdsservice.model.rules.OutcomeStatus;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.rules.expression.ExpressionParseException;
import org.snomed.cdsservice.service.rules.expression.RuleExpression;
import org.snomed.cdsservice.service.rules.expression.RuleExpressionParser;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads and structurally validates the diagnostic criteria and rules TSV files at application startup.
 * <p>
 * Loading is purely structural: it parses fields, parses the Boolean rule expressions into an AST and
 * verifies that every referenced criterion exists. Terminology expansion of value sets is performed
 * separately (by the diagnostic engine) so that this loader can be unit tested without a terminology
 * server. Any structural problem fails fast with a {@link ServiceException} naming the file and row.
 */
@Service
public class RuleLoaderService {

	static final String[] CRITERIA_HEADERS = {
			"criterion_id", "label", "criterion_type", "resource_type", "code_system", "code_selector_type",
			"code_selector", "code_match", "component_code_system", "component_code", "operator", "value_low",
			"value_high", "lower_inclusive", "upper_inclusive", "unit_system", "unit_code", "unit_display",
			"unit_snomed_code", "accepted_statuses", "time_aspect_system", "time_aspect_code", "aggregation_system",
			"aggregation_code", "min_occurrences", "distinct_by", "context_source", "context_key", "context_operator",
			"context_value", "missing_data_behavior", "notes"
	};

	static final String[] RULES_HEADERS = {
			"rule_id", "card_uuid", "version", "enabled", "service_id", "hook", "pathway", "trigger_event",
			"logic_expression", "outcome_status", "action_type", "outcome_code_system", "outcome_code",
			"outcome_display", "suppress_if_outcome_present", "card_indicator", "card_summary", "card_detail",
			"source_label", "source_url", "implementation_notes"
	};

	private final Logger logger = LoggerFactory.getLogger(getClass());

	/**
	 * Load and validate one diagnostic domain from its criteria and rules files.
	 */
	public RuleSet loadRuleSet(String domain, String criteriaTsvPath, String rulesTsvPath) throws ServiceException {
		Map<String, Criterion> criteria = loadCriteria(criteriaTsvPath);
		List<Rule> rules = loadRules(rulesTsvPath, criteria);
		logger.info("Loaded diagnostic domain '{}': {} criteria, {} rules ({} enabled).",
				domain, criteria.size(), rules.size(), rules.stream().filter(Rule::enabled).count());
		return new RuleSet(domain, criteria, rules);
	}

	private Map<String, Criterion> loadCriteria(String tsvPath) throws ServiceException {
		Map<String, Criterion> criteria = new LinkedHashMap<>();
		List<Row> rows = readRows(tsvPath, CRITERIA_HEADERS);
		for (Row row : rows) {
			Criterion criterion = parseCriterion(row);
			if (criteria.containsKey(criterion.criterionId())) {
				throw row.error("Duplicate criterion_id '%s'.".formatted(criterion.criterionId()));
			}
			criteria.put(criterion.criterionId(), criterion);
		}
		return criteria;
	}

	private Criterion parseCriterion(Row row) throws ServiceException {
		String criterionId = row.required("criterion_id");
		String label = row.required("label");
		CriterionType criterionType = row.requiredEnum("criterion_type", CriterionType.class);

		CodeSelectorType codeSelectorType = row.optionalEnum("code_selector_type", CodeSelectorType.class);
		CodeMatchType codeMatch = row.optionalEnum("code_match", CodeMatchType.class);
		CriterionOperator operator = row.requiredEnum("operator", CriterionOperator.class);
		CriterionOperator contextOperator = row.optionalEnum("context_operator", CriterionOperator.class);

		BigDecimal valueLow = row.optionalBigDecimal("value_low");
		BigDecimal valueHigh = row.optionalBigDecimal("value_high");
		Boolean lowerInclusive = row.optionalBoolean("lower_inclusive");
		Boolean upperInclusive = row.optionalBoolean("upper_inclusive");
		int minOccurrences = row.optionalPositiveInt("min_occurrences", 1);
		DistinctBy distinctBy = row.optionalEnumWithDefault("distinct_by", DistinctBy.class, DistinctBy.NONE);
		MissingDataBehavior missingDataBehavior = row.optionalEnumWithDefault("missing_data_behavior", MissingDataBehavior.class, MissingDataBehavior.UNKNOWN);

		Criterion criterion = new Criterion(
				criterionId,
				label,
				criterionType,
				row.get("resource_type"),
				row.get("code_system"),
				codeSelectorType,
				row.get("code_selector"),
				codeMatch,
				row.get("component_code_system"),
				row.get("component_code"),
				operator,
				valueLow,
				valueHigh,
				lowerInclusive,
				upperInclusive,
				row.get("unit_system"),
				row.get("unit_code"),
				row.get("unit_display"),
				row.get("unit_snomed_code"),
				parseStatuses(row.get("accepted_statuses")),
				row.get("time_aspect_system"),
				row.get("time_aspect_code"),
				row.get("aggregation_system"),
				row.get("aggregation_code"),
				minOccurrences,
				distinctBy,
				row.get("context_source"),
				row.get("context_key"),
				contextOperator,
				row.get("context_value"),
				missingDataBehavior,
				row.get("notes")
		);
		validateCriterionConsistency(row, criterion);
		return criterion;
	}

	private void validateCriterionConsistency(Row row, Criterion c) throws ServiceException {
		switch (c.criterionType()) {
			case OBSERVATION_VALUE -> {
				requireField(row, "resource_type", c.resourceType());
				requireField(row, "code_selector", c.codeSelector());
				validateNumericOperator(row, c);
			}
			case OBSERVATION_COMPONENT_VALUE -> {
				requireField(row, "resource_type", c.resourceType());
				requireField(row, "code_selector", c.codeSelector());
				requireField(row, "component_code_system", c.componentCodeSystem());
				requireField(row, "component_code", c.componentCode());
				validateNumericOperator(row, c);
			}
			case CODED_RESOURCE_PRESENT -> {
				requireField(row, "resource_type", c.resourceType());
				requireField(row, "code_selector", c.codeSelector());
				if (c.operator() != CriterionOperator.EXISTS && c.operator() != CriterionOperator.NOT_EXISTS) {
					throw row.error("CODED_RESOURCE_PRESENT criteria must use operator 'exists' or 'not_exists' but found '%s'.".formatted(c.operator()));
				}
			}
			case CONTEXT_VALUE -> {
				requireField(row, "context_key", c.contextKey());
				requireField(row, "context_value", c.contextValue());
				if (c.contextOperator() != CriterionOperator.EQ) {
					throw row.error("CONTEXT_VALUE criteria must use context_operator 'eq' but found '%s'.".formatted(c.contextOperator()));
				}
			}
		}
	}

	private void validateNumericOperator(Row row, Criterion c) throws ServiceException {
		switch (c.operator()) {
			case GE, GT, LE, LT, EQ -> {
				if (c.valueLow() == null) {
					throw row.error("Operator '%s' requires value_low.".formatted(c.operator()));
				}
			}
			case BETWEEN -> {
				if (c.valueLow() == null || c.valueHigh() == null) {
					throw row.error("Operator 'between' requires both value_low and value_high.");
				}
				if (c.valueLow().compareTo(c.valueHigh()) >= 0) {
					throw row.error("Operator 'between' requires value_low (%s) < value_high (%s).".formatted(c.valueLow(), c.valueHigh()));
				}
				if (c.lowerInclusive() == null || c.upperInclusive() == null) {
					throw row.error("Operator 'between' requires both lower_inclusive and upper_inclusive flags.");
				}
			}
			case EXISTS, NOT_EXISTS -> {
				// No numeric bounds required.
			}
		}
	}

	private List<Rule> loadRules(String tsvPath, Map<String, Criterion> criteria) throws ServiceException {
		List<Rule> rules = new ArrayList<>();
		Set<String> ruleIds = new HashSet<>();
		Set<String> cardUuids = new HashSet<>();
		List<Row> rows = readRows(tsvPath, RULES_HEADERS);
		for (Row row : rows) {
			Rule rule = parseRule(row, criteria);
			if (!ruleIds.add(rule.ruleId())) {
				throw row.error("Duplicate rule_id '%s'.".formatted(rule.ruleId()));
			}
			if (!cardUuids.add(rule.cardUuid())) {
				throw row.error("Duplicate card_uuid '%s'.".formatted(rule.cardUuid()));
			}
			rules.add(rule);
		}
		return rules;
	}

	private Rule parseRule(Row row, Map<String, Criterion> criteria) throws ServiceException {
		String ruleId = row.required("rule_id");
		String cardUuid = row.required("card_uuid");
		boolean enabled = row.optionalBooleanWithDefault("enabled", false);
		String logicExpression = row.required("logic_expression");

		RuleExpression expression;
		try {
			expression = RuleExpressionParser.parse(logicExpression);
		} catch (ExpressionParseException e) {
			throw row.error("Invalid logic_expression: " + e.getMessage());
		}
		for (String referencedId : expression.referencedCriterionIds()) {
			if (!criteria.containsKey(referencedId)) {
				throw row.error("logic_expression references unknown criterion '%s'.".formatted(referencedId));
			}
		}

		OutcomeStatus outcomeStatus = row.requiredEnum("outcome_status", OutcomeStatus.class);
		ActionType actionType = row.requiredEnum("action_type", ActionType.class);
		CDSIndicator cardIndicator = parseIndicator(row);
		boolean suppressIfOutcomePresent = row.optionalBooleanWithDefault("suppress_if_outcome_present", false);
		String outcomeCode = row.get("outcome_code");

		if (enabled) {
			requireField(row, "service_id", row.get("service_id"));
			requireField(row, "hook", row.get("hook"));
			requireField(row, "card_summary", row.get("card_summary"));
			requireField(row, "card_detail", row.get("card_detail"));
			requireField(row, "source_label", row.get("source_label"));
			requireField(row, "outcome_display", row.get("outcome_display"));
			if ((suppressIfOutcomePresent || actionType == ActionType.CREATE_CONDITION) && Strings.isNullOrEmpty(outcomeCode)) {
				throw row.error("Enabled rule requires outcome_code when suppress_if_outcome_present is true or action_type is create_condition.");
			}
		}

		return new Rule(
				ruleId,
				cardUuid,
				row.get("version"),
				enabled,
				row.get("service_id"),
				row.get("hook"),
				row.get("pathway"),
				row.get("trigger_event"),
				logicExpression,
				expression,
				outcomeStatus,
				actionType,
				row.get("outcome_code_system"),
				outcomeCode,
				row.get("outcome_display"),
				suppressIfOutcomePresent,
				cardIndicator,
				row.get("card_summary"),
				row.get("card_detail"),
				row.get("source_label"),
				row.get("source_url"),
				row.get("implementation_notes")
		);
	}

	private CDSIndicator parseIndicator(Row row) throws ServiceException {
		String value = row.required("card_indicator");
		try {
			return CDSIndicator.valueOf(value.trim());
		} catch (IllegalArgumentException e) {
			throw row.error("Unsupported card_indicator '%s'. Expected one of %s.".formatted(value, Arrays.toString(CDSIndicator.values())));
		}
	}

	private Set<String> parseStatuses(String raw) {
		Set<String> statuses = new HashSet<>();
		if (!Strings.isNullOrEmpty(raw)) {
			for (String status : raw.split("\\|")) {
				String trimmed = status.trim();
				if (!trimmed.isEmpty()) {
					statuses.add(trimmed.toLowerCase());
				}
			}
		}
		return statuses;
	}

	private void requireField(Row row, String column, String value) throws ServiceException {
		if (Strings.isNullOrEmpty(value) || value.isBlank()) {
			throw row.error("Missing required value for '%s'.".formatted(column));
		}
	}

	private List<Row> readRows(String tsvPath, String[] requiredHeaders) throws ServiceException {
		List<Row> rows = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(tsvPath))) {
			String headerLine = reader.readLine();
			if (headerLine == null) {
				throw new ServiceException("Rules file '%s' is empty.".formatted(tsvPath));
			}
			Map<String, Integer> headerIndex = indexHeaders(tsvPath, headerLine, requiredHeaders);

			String line;
			int lineNumber = 1;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				if (line.isBlank()) {
					continue;
				}
				String[] values = line.split("\t", -1);
				rows.add(new Row(tsvPath, lineNumber, headerIndex, values));
			}
		} catch (IOException e) {
			throw new ServiceException("Failed to read diagnostic rules file '%s'.".formatted(tsvPath), e);
		}
		return rows;
	}

	private Map<String, Integer> indexHeaders(String tsvPath, String headerLine, String[] requiredHeaders) throws ServiceException {
		String[] headers = headerLine.split("\t", -1);
		Map<String, Integer> headerIndex = new HashMap<>();
		for (int i = 0; i < headers.length; i++) {
			headerIndex.put(headers[i].trim(), i);
		}
		List<String> missing = new ArrayList<>();
		for (String required : requiredHeaders) {
			if (!headerIndex.containsKey(required)) {
				missing.add(required);
			}
		}
		if (!missing.isEmpty()) {
			throw new ServiceException("Rules file '%s' is missing required column(s): %s".formatted(tsvPath, missing));
		}
		return headerIndex;
	}

	/**
	 * A single data row with header-name access and file/row context for error reporting.
	 */
	private static final class Row {
		private final String fileName;
		private final int lineNumber;
		private final Map<String, Integer> headerIndex;
		private final String[] values;

		private Row(String fileName, int lineNumber, Map<String, Integer> headerIndex, String[] values) {
			this.fileName = fileName;
			this.lineNumber = lineNumber;
			this.headerIndex = headerIndex;
			this.values = values;
		}

		String get(String column) {
			Integer index = headerIndex.get(column);
			if (index == null || index >= values.length) {
				return "";
			}
			return values[index].trim();
		}

		String required(String column) throws ServiceException {
			String value = get(column);
			if (value.isBlank()) {
				throw error("Missing required value for '%s'.".formatted(column));
			}
			return value;
		}

		<E extends Enum<E>> E requiredEnum(String column, Class<E> enumType) throws ServiceException {
			return toEnum(column, required(column), enumType);
		}

		<E extends Enum<E>> E optionalEnum(String column, Class<E> enumType) throws ServiceException {
			String value = get(column);
			return value.isBlank() ? null : toEnum(column, value, enumType);
		}

		<E extends Enum<E>> E optionalEnumWithDefault(String column, Class<E> enumType, E defaultValue) throws ServiceException {
			String value = get(column);
			return value.isBlank() ? defaultValue : toEnum(column, value, enumType);
		}

		private <E extends Enum<E>> E toEnum(String column, String value, Class<E> enumType) throws ServiceException {
			try {
				return Enum.valueOf(enumType, value.trim().toUpperCase());
			} catch (IllegalArgumentException e) {
				throw error("Unsupported value '%s' for '%s'. Expected one of %s.".formatted(value, column, Arrays.toString(enumType.getEnumConstants())));
			}
		}

		BigDecimal optionalBigDecimal(String column) throws ServiceException {
			String value = get(column);
			if (value.isBlank()) {
				return null;
			}
			try {
				return new BigDecimal(value);
			} catch (NumberFormatException e) {
				throw error("Value '%s' for '%s' is not a valid number.".formatted(value, column));
			}
		}

		Boolean optionalBoolean(String column) throws ServiceException {
			String value = get(column);
			if (value.isBlank()) {
				return null;
			}
			return parseStrictBoolean(column, value);
		}

		boolean optionalBooleanWithDefault(String column, boolean defaultValue) throws ServiceException {
			String value = get(column);
			if (value.isBlank()) {
				return defaultValue;
			}
			return parseStrictBoolean(column, value);
		}

		private boolean parseStrictBoolean(String column, String value) throws ServiceException {
			String normalised = value.trim().toLowerCase();
			if (normalised.equals("true")) {
				return true;
			}
			if (normalised.equals("false")) {
				return false;
			}
			throw error("Value '%s' for '%s' must be 'true' or 'false'.".formatted(value, column));
		}

		int optionalPositiveInt(String column, int defaultValue) throws ServiceException {
			String value = get(column);
			if (value.isBlank()) {
				return defaultValue;
			}
			int parsed;
			try {
				parsed = Integer.parseInt(value.trim());
			} catch (NumberFormatException e) {
				throw error("Value '%s' for '%s' is not a valid integer.".formatted(value, column));
			}
			if (parsed < 1) {
				throw error("Value '%s' for '%s' must be at least 1.".formatted(value, column));
			}
			return parsed;
		}

		ServiceException error(String message) {
			return new ServiceException("%s (file '%s', row %d)".formatted(message, fileName, lineNumber));
		}
	}
}
