package org.snomed.cdsservice.service.rules;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.rules.ActionType;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.OutcomeStatus;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleEvaluationResult;
import org.snomed.cdsservice.model.rules.TruthValue;
import org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport;
import org.snomed.cdsservice.service.rules.expression.RuleExpressionParser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleCardFactoryTest {

	private final RuleCardFactory factory = new RuleCardFactory();

	@Test
	void resolvesPlaceholdersFromContributingCriteria() {
		Criterion a = EvaluatorTestSupport.namedCriterion("DM_FBG_GE_7");
		Rule rule = rule("Diagnostic criterion met: {{MatchedCriteria}} → {{OutcomeDisplay}}.",
				"The {{OutcomeDisplay}} criterion is met.");
		RuleEvaluationResult result = new RuleEvaluationResult(rule, TruthValue.TRUE,
				List.of(trueResult(a)), List.of(trueResult(a)));

		CDSCard card = factory.createCard(result);

		assertEquals("Diagnostic criterion met: Criterion DM_FBG_GE_7 → Type 2 diabetes mellitus.", card.getSummary());
		assertEquals("The Type 2 diabetes mellitus criterion is met.", card.getDetail());
		assertEquals(CDSIndicator.info, card.getIndicator());
		assertEquals("uuid", card.getUuid());
		assertEquals("source", card.getSource().getLabel());
	}

	@Test
	void producesAFreshCardInstancePerCall() {
		Criterion a = EvaluatorTestSupport.namedCriterion("A");
		Rule rule = rule("{{MatchedCriteria}}", "{{OutcomeDisplay}}");
		RuleEvaluationResult result = new RuleEvaluationResult(rule, TruthValue.TRUE, List.of(trueResult(a)), List.of(trueResult(a)));

		CDSCard first = factory.createCard(result);
		CDSCard second = factory.createCard(result);
		assertFalse(first == second);
		// No mutable reference lists are shared with a template card.
		assertTrue(first.getReferenceConditions() == null);
	}

	private CriterionEvaluationResult trueResult(Criterion criterion) {
		return CriterionEvaluationResult.of(criterion, TruthValue.TRUE, List.of(), "matched");
	}

	private Rule rule(String summaryTemplate, String detailTemplate) {
		return new Rule("R", "uuid", "1.0.0", true, "svc", "patient-view", "path", "trigger",
				"A", RuleExpressionParser.parse("A"), OutcomeStatus.DIAGNOSTIC, ActionType.CREATE_CONDITION,
				"http://snomed.info/sct", "44054006", "Type 2 diabetes mellitus", true, CDSIndicator.info,
				summaryTemplate, detailTemplate, "source", "https://example.org/", "notes");
	}
}
