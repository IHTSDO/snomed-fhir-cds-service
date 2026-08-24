package org.snomed.cdsservice.service.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snomed.cdsservice.model.rules.CriterionType;
import org.snomed.cdsservice.model.rules.ActionType;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleSet;
import org.snomed.cdsservice.model.rules.OutcomeStatus;
import org.snomed.cdsservice.service.ServiceException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleLoaderServiceTest {

	private static final String DIABETES_CRITERIA = "src/test/resources/diagnostic-rules/diabetes_criteria.tsv";
	private static final String DIABETES_RULES = "src/test/resources/diagnostic-rules/diabetes_rules.tsv";
	private static final String HYPERTENSION_CRITERIA = "src/test/resources/diagnostic-rules/hypertension_criteria.tsv";
	private static final String HYPERTENSION_RULES = "src/test/resources/diagnostic-rules/hypertension_rules.tsv";

	private final RuleLoaderService loader = new RuleLoaderService();

	@TempDir
	Path tempDir;

	@Test
	void loadsValidDiabetesFiles() throws ServiceException {
		RuleSet ruleSet = loader.loadRuleSet("diabetes", DIABETES_CRITERIA, DIABETES_RULES);

		assertEquals(8, ruleSet.criteria().size());
		assertEquals(5, ruleSet.rules().size());
		assertEquals(5, ruleSet.enabledRules().size());

		Criterion fbg = ruleSet.criteria().get("DM_FBG_GE_7");
		assertNotNull(fbg);
		assertEquals(CriterionType.OBSERVATION_VALUE, fbg.criterionType());
		assertEquals(0, new BigDecimal("7.0").compareTo(fbg.valueLow()));
		assertEquals("mmol/L", fbg.unitCode());
		assertTrue(fbg.acceptedStatuses().contains("final"));

		Rule ogtt = ruleSet.rules().stream()
				.filter(r -> r.ruleId().equals("DM-2024-OGTT-01")).findFirst().orElseThrow();
		assertEquals(OutcomeStatus.DIAGNOSTIC, ogtt.outcomeStatus());
		assertEquals(ActionType.CREATE_CONDITION, ogtt.actionType());
		assertNotNull(ogtt.expression());
		// The OGTT expression must reference the criteria it names.
		assertTrue(ogtt.expression().referencedCriterionIds().contains("DM_OGTT_2H_GE_11_1_TWO_OCCASIONS"));
		assertTrue(ruleSet.criteria().keySet().containsAll(ogtt.expression().referencedCriterionIds()));
	}

	@Test
	void loadsValidHypertensionFiles() throws ServiceException {
		RuleSet ruleSet = loader.loadRuleSet("hypertension", HYPERTENSION_CRITERIA, HYPERTENSION_RULES);

		assertEquals(9, ruleSet.criteria().size());
		assertEquals(4, ruleSet.rules().size());

		Criterion clinicSys = ruleSet.criteria().get("HTN_CLINIC_SYS_140_TO_LT_180");
		assertNotNull(clinicSys);
		assertEquals(CriterionType.OBSERVATION_COMPONENT_VALUE, clinicSys.criterionType());
		// Suspected-range upper bound must remain exclusive so severe hypertension stays outside this rule.
		assertEquals(Boolean.TRUE, clinicSys.lowerInclusive());
		assertEquals(Boolean.FALSE, clinicSys.upperInclusive());
		assertEquals(0, new BigDecimal("140").compareTo(clinicSys.valueLow()));
		assertEquals(0, new BigDecimal("180").compareTo(clinicSys.valueHigh()));

		Criterion context = ruleSet.criteria().get("HTN_ABPM_HBPM_UNAVAILABLE_OR_IMPRACTICAL");
		assertEquals(CriterionType.CONTEXT_VALUE, context.criterionType());
		assertEquals("abpmHbpUnavailableOrImpractical", context.contextKey());
		assertEquals("true", context.contextValue());
	}

	@Test
	void rejectsMissingRequiredHeader() throws IOException {
		Map<String, String> criterion = validCriterion();
		Path criteriaFile = writeCriteria(List.of(criterion), "criterion_type");// drop a required column
		Path rulesFile = writeRules(List.of(validRule()));
		ServiceException e = assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
		assertTrue(e.getMessage().contains("criterion_type"), e.getMessage());
	}

	@Test
	void rejectsDuplicateCriterionId() throws IOException {
		Path criteriaFile = writeCriteria(List.of(validCriterion(), validCriterion()));
		Path rulesFile = writeRules(List.of(validRule()));
		ServiceException e = assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
		assertTrue(e.getMessage().contains("Duplicate criterion_id"), e.getMessage());
	}

	@Test
	void rejectsUnknownCriterionReference() throws IOException {
		Path criteriaFile = writeCriteria(List.of(validCriterion()));
		Map<String, String> rule = validRule();
		rule.put("logic_expression", "DOES_NOT_EXIST");
		Path rulesFile = writeRules(List.of(rule));
		ServiceException e = assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
		assertTrue(e.getMessage().contains("unknown criterion"), e.getMessage());
	}

	@Test
	void rejectsUnsupportedOperator() throws IOException {
		Map<String, String> criterion = validCriterion();
		criterion.put("operator", "greater_than");// not a supported operator token
		Path criteriaFile = writeCriteria(List.of(criterion));
		Path rulesFile = writeRules(List.of(validRule()));
		assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
	}

	@Test
	void rejectsInvalidNumericValue() throws IOException {
		Map<String, String> criterion = validCriterion();
		criterion.put("value_low", "not-a-number");
		Path criteriaFile = writeCriteria(List.of(criterion));
		Path rulesFile = writeRules(List.of(validRule()));
		assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
	}

	@Test
	void rejectsInvalidBooleanFlag() throws IOException {
		Map<String, String> rule = validRule();
		rule.put("enabled", "maybe");
		Path criteriaFile = writeCriteria(List.of(validCriterion()));
		Path rulesFile = writeRules(List.of(rule));
		assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
	}

	@Test
	void rejectsUnparsableExpression() throws IOException {
		Map<String, String> rule = validRule();
		rule.put("logic_expression", "DM_TEST_FBG AND");
		Path criteriaFile = writeCriteria(List.of(validCriterion()));
		Path rulesFile = writeRules(List.of(rule));
		ServiceException e = assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
		assertTrue(e.getMessage().contains("logic_expression"), e.getMessage());
	}

	@Test
	void rejectsInvalidMinOccurrences() throws IOException {
		Map<String, String> criterion = validCriterion();
		criterion.put("min_occurrences", "0");
		Path criteriaFile = writeCriteria(List.of(criterion));
		Path rulesFile = writeRules(List.of(validRule()));
		assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
	}

	@Test
	void rejectsBetweenWithoutUpperBound() throws IOException {
		Map<String, String> criterion = validCriterion();
		criterion.put("operator", "between");
		criterion.put("value_high", "");
		criterion.put("upper_inclusive", "");
		Path criteriaFile = writeCriteria(List.of(criterion));
		Path rulesFile = writeRules(List.of(validRule()));
		assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
	}

	@Test
	void rejectsComponentValueWithoutComponentCode() throws IOException {
		Map<String, String> criterion = validCriterion();
		criterion.put("criterion_type", "OBSERVATION_COMPONENT_VALUE");
		criterion.put("component_code_system", "");
		criterion.put("component_code", "");
		Path criteriaFile = writeCriteria(List.of(criterion));
		Path rulesFile = writeRules(List.of(validRule()));
		assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
	}

	@Test
	void rejectsDuplicateCardUuid() throws IOException {
		Map<String, String> ruleA = validRule();
		ruleA.put("rule_id", "R-A");
		Map<String, String> ruleB = validRule();
		ruleB.put("rule_id", "R-B");// distinct rule id, same card_uuid
		Path criteriaFile = writeCriteria(List.of(validCriterion()));
		Path rulesFile = writeRules(List.of(ruleA, ruleB));
		ServiceException e = assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
		assertTrue(e.getMessage().contains("card_uuid"), e.getMessage());
	}

	@Test
	void rejectsEnabledRuleMissingCardSummary() throws IOException {
		Map<String, String> rule = validRule();
		rule.put("card_summary", "");
		Path criteriaFile = writeCriteria(List.of(validCriterion()));
		Path rulesFile = writeRules(List.of(rule));
		assertThrows(ServiceException.class, () -> loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString()));
	}

	@Test
	void disabledRuleWithMissingPresentationStillLoads() throws IOException, ServiceException {
		Map<String, String> rule = validRule();
		rule.put("enabled", "false");
		rule.put("card_summary", "");
		rule.put("source_label", "");
		Path criteriaFile = writeCriteria(List.of(validCriterion()));
		Path rulesFile = writeRules(List.of(rule));
		RuleSet ruleSet = loader.loadRuleSet("t", criteriaFile.toString(), rulesFile.toString());
		assertEquals(1, ruleSet.rules().size());
		assertFalse(ruleSet.rules().get(0).enabled());
		assertTrue(ruleSet.enabledRules().isEmpty());
	}

	// --- fixture builders -------------------------------------------------------------------------

	private Map<String, String> validCriterion() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("criterion_id", "DM_TEST_FBG");
		values.put("label", "Test fasting glucose at or above 7.0 mmol/L");
		values.put("criterion_type", "OBSERVATION_VALUE");
		values.put("resource_type", "Observation");
		values.put("code_system", "http://snomed.info/sct");
		values.put("code_selector_type", "code");
		values.put("code_selector", "271062006");
		values.put("code_match", "exact");
		values.put("operator", "ge");
		values.put("value_low", "7.0");
		values.put("lower_inclusive", "true");
		values.put("unit_system", "http://unitsofmeasure.org");
		values.put("unit_code", "mmol/L");
		values.put("accepted_statuses", "final|amended|corrected");
		values.put("min_occurrences", "1");
		values.put("missing_data_behavior", "UNKNOWN");
		values.put("notes", "Test criterion");
		return values;
	}

	private Map<String, String> validRule() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put("rule_id", "R-TEST-01");
		values.put("card_uuid", "11111111-1111-1111-1111-111111111111");
		values.put("version", "1.0.0");
		values.put("enabled", "true");
		values.put("service_id", "diagnostic-support-test");
		values.put("hook", "patient-view");
		values.put("pathway", "Test pathway");
		values.put("trigger_event", "Test");
		values.put("logic_expression", "DM_TEST_FBG");
		values.put("outcome_status", "diagnostic");
		values.put("action_type", "create_condition");
		values.put("outcome_code_system", "http://snomed.info/sct");
		values.put("outcome_code", "44054006");
		values.put("outcome_display", "Type 2 diabetes mellitus");
		values.put("suppress_if_outcome_present", "true");
		values.put("card_indicator", "info");
		values.put("card_summary", "Test summary");
		values.put("card_detail", "Test detail");
		values.put("source_label", "Test guideline");
		values.put("source_url", "https://example.org/");
		values.put("implementation_notes", "Test rule");
		return values;
	}

	private Path writeCriteria(List<Map<String, String>> rows, String... omitHeaders) throws IOException {
		return writeTsv("criteria.tsv", RuleLoaderService.CRITERIA_HEADERS, rows, omitHeaders);
	}

	private Path writeRules(List<Map<String, String>> rows, String... omitHeaders) throws IOException {
		return writeTsv("rules.tsv", RuleLoaderService.RULES_HEADERS, rows, omitHeaders);
	}

	private Path writeTsv(String fileName, String[] headers, List<Map<String, String>> rows, String... omitHeaders) throws IOException {
		List<String> omit = List.of(omitHeaders);
		List<String> keptHeaders = new ArrayList<>();
		for (String header : headers) {
			if (!omit.contains(header)) {
				keptHeaders.add(header);
			}
		}
		StringBuilder sb = new StringBuilder(String.join("\t", keptHeaders)).append("\n");
		for (Map<String, String> row : rows) {
			String line = keptHeaders.stream().map(h -> row.getOrDefault(h, "")).collect(Collectors.joining("\t"));
			sb.append(line).append("\n");
		}
		Path file = tempDir.resolve(fileName);
		Files.writeString(file, sb.toString());
		return file;
	}
}
