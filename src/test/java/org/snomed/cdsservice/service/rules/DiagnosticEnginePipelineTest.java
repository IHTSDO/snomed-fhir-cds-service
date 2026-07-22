package org.snomed.cdsservice.service.rules;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.rules.ActionType;
import org.snomed.cdsservice.model.rules.OutcomeStatus;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleEvaluationResult;
import org.snomed.cdsservice.model.rules.RuleSet;
import org.snomed.cdsservice.model.rules.TruthValue;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.rules.evaluator.CodedResourcePresentCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.CodingBasedSemanticQualifierMatcher;
import org.snomed.cdsservice.service.rules.evaluator.ContextValueCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.CriterionEvaluatorRegistry;
import org.snomed.cdsservice.service.rules.evaluator.ObservationComponentValueCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.ObservationCriterionSupport;
import org.snomed.cdsservice.service.rules.evaluator.ObservationValueCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.RuleEvaluationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.SNOMED;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.UCUM;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.addComponent;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.bpObservation;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.condition;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observation;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.withCoding;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.withEncounter;

/**
 * End-to-end engine test over the real diabetes and hypertension rule content, exercising the full
 * pipeline (loader -> ECL resolver -> evaluators -> engine -> card factory) with hand-built FHIR
 * resources. Because the content uses only locally-resolvable ECL, no terminology server is contacted.
 */
class DiagnosticEnginePipelineTest {

	private static final String DIR = "src/test/resources/diagnostic-rules/";

	private RuleSet diabetes;
	private RuleSet hypertension;
	private RuleEngine engine;
	private final RuleCardFactory cardFactory = new RuleCardFactory();

	@BeforeEach
	void setUp() throws ServiceException {
		RuleLoaderService loader = new RuleLoaderService();
		diabetes = loader.loadRuleSet("diabetes", DIR + "diabetes_criteria.tsv", DIR + "diabetes_rules.tsv");
		hypertension = loader.loadRuleSet("hypertension", DIR + "hypertension_criteria.tsv", DIR + "hypertension_rules.tsv");

		// A null terminology client is safe: all supplied content resolves locally, so warm-up is a no-op.
		EclCodeResolver resolver = new EclCodeResolver(null);
		resolver.warmUp(diabetes.criteria().values());
		resolver.warmUp(hypertension.criteria().values());

		ObservationCriterionSupport support = new ObservationCriterionSupport(resolver, new CodingBasedSemanticQualifierMatcher());
		CriterionEvaluatorRegistry registry = new CriterionEvaluatorRegistry(List.of(
				new ObservationValueCriterionEvaluator(support),
				new ObservationComponentValueCriterionEvaluator(support),
				new CodedResourcePresentCriterionEvaluator(resolver),
				new ContextValueCriterionEvaluator()));
		engine = new RuleEngine(registry);
	}

	@Test
	void fastingGlucoseAtThresholdFiresDiagnosticCondition() {
		RuleEvaluationResult result = evaluate(diabetes, "DM-2024-FBG-01",
				context(Map.of(), observation("271062006", Observation.ObservationStatus.FINAL, "7.4", UCUM, "mmol/L", "2024-05-01")));

		assertEquals(TruthValue.TRUE, result.truthValue());
		assertEquals(OutcomeStatus.DIAGNOSTIC, result.rule().outcomeStatus());
		assertEquals(ActionType.CREATE_CONDITION, result.rule().actionType());

		CDSCard card = cardFactory.createCard(result);
		assertTrue(card.getSummary().contains("Type 2 diabetes mellitus"), card.getSummary());
	}

	@Test
	void fastingGlucoseBelowThresholdDoesNotFire() {
		RuleEvaluationResult result = evaluate(diabetes, "DM-2024-FBG-01",
				context(Map.of(), observation("271062006", Observation.ObservationStatus.FINAL, "6.4", UCUM, "mmol/L", "2024-05-01")));
		assertEquals(TruthValue.FALSE, result.truthValue());
	}

	@Test
	void randomGlucoseWithClassicSymptomFiresDiagnostic() {
		RuleEvaluationResult result = evaluate(diabetes, "DM-2024-RANDOM-01", context(Map.of(),
				observation("271061004", Observation.ObservationStatus.FINAL, "12.0", UCUM, "mmol/L", "2024-05-01"),
				condition("28442001", "active", "confirmed"))); // Polyuria
		assertEquals(TruthValue.TRUE, result.truthValue());
	}

	@Test
	void randomGlucoseWithoutSymptomOrCrisisDoesNotFire() {
		RuleEvaluationResult result = evaluate(diabetes, "DM-2024-RANDOM-01",
				context(Map.of(), observation("271061004", Observation.ObservationStatus.FINAL, "12.0", UCUM, "mmol/L", "2024-05-01")));
		assertEquals(TruthValue.UNKNOWN, result.truthValue());
	}

	@Test
	void suspectedHypertensionFromSystolicOnly() {
		Observation clinicBp = addComponent(bpObservation("75367002", "2024-05-01"), "271649006", "150");
		RuleEvaluationResult result = evaluate(hypertension, "HTN-2024-CLINIC-SUSPECTED-01", context(Map.of(), clinicBp));
		assertEquals(TruthValue.TRUE, result.truthValue());
		assertEquals(OutcomeStatus.SUSPECTED, result.rule().outcomeStatus());
		assertEquals(ActionType.RECOMMEND_CONFIRMATORY_TEST, result.rule().actionType());
	}

	@Test
	void suspectedHypertensionFromDiastolicOnly() {
		Observation clinicBp = addComponent(bpObservation("75367002", "2024-05-01"), "271650006", "100");
		RuleEvaluationResult result = evaluate(hypertension, "HTN-2024-CLINIC-SUSPECTED-01", context(Map.of(), clinicBp));
		assertEquals(TruthValue.TRUE, result.truthValue());
	}

	@Test
	void ambulatoryAverageWithQualifiersFiresDiagnostic() {
		// Parent ABPM code plus the 24-hour and mean qualifier concepts carried on Observation.code.
		Observation abpm = addComponent(bpObservation("164783007", "2024-05-01"), "271649006", "132");
		withCoding(abpm, SNOMED, "255250005"); // 24 hour study
		withCoding(abpm, SNOMED, "373098007"); // mean/average
		RuleEvaluationResult result = evaluate(hypertension, "HTN-2024-ABPM-01", context(Map.of(), abpm));
		assertEquals(TruthValue.TRUE, result.truthValue());
		assertEquals(OutcomeStatus.DIAGNOSTIC, result.rule().outcomeStatus());
	}

	@Test
	void repeatClinicRequiresExplicitContext() {
		Observation visit1 = withEncounter(addComponent(bpObservation("75367002", "2024-05-01"), "271649006", "150"), "Encounter/1");
		Observation visit2 = withEncounter(addComponent(bpObservation("75367002", "2024-06-01"), "271649006", "148"), "Encounter/2");

		// Without the explicit context value the rule must not fire (missing context -> UNKNOWN).
		RuleEvaluationResult withoutContext = evaluate(hypertension, "HTN-2024-REPEAT-CLINIC-01", context(Map.of(), visit1, visit2));
		assertEquals(TruthValue.UNKNOWN, withoutContext.truthValue());

		// With the explicit context value the diagnostic fallback fires.
		RuleEvaluationResult withContext = evaluate(hypertension, "HTN-2024-REPEAT-CLINIC-01",
				context(Map.of("abpmHbpUnavailableOrImpractical", true), visit1, visit2));
		assertEquals(TruthValue.TRUE, withContext.truthValue());
		assertEquals(OutcomeStatus.DIAGNOSTIC, withContext.rule().outcomeStatus());
	}

	private RuleEvaluationResult evaluate(RuleSet ruleSet, String ruleId, RuleEvaluationContext context) {
		Rule rule = ruleSet.rules().stream().filter(r -> r.ruleId().equals(ruleId)).findFirst().orElseThrow();
		return engine.evaluateRule(rule, ruleSet.criteria(), context);
	}

	private RuleEvaluationContext context(Map<String, Object> hookContext, Resource... resources) {
		return new RuleEvaluationContext(null, List.of(resources), hookContext);
	}
}
