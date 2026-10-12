package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.CommunicationRequest;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.snomed.cdsservice.model.rules.Rule;
import org.snomed.cdsservice.model.rules.RuleEvaluationResult;
import org.snomed.cdsservice.model.rules.RuleSet;
import org.snomed.cdsservice.model.rules.TruthValue;
import org.snomed.cdsservice.service.rules.EclCodeResolver;
import org.snomed.cdsservice.service.rules.RuleCardFactory;
import org.snomed.cdsservice.service.rules.RuleEngine;
import org.snomed.cdsservice.service.rules.RuleLoaderService;
import org.snomed.cdsservice.service.rules.evaluator.CodedResourcePresentCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.CodingBasedSemanticQualifierMatcher;
import org.snomed.cdsservice.service.rules.evaluator.ContextValueCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.CriterionEvaluatorRegistry;
import org.snomed.cdsservice.service.rules.evaluator.ObservationComponentValueCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.ObservationCriterionSupport;
import org.snomed.cdsservice.service.rules.evaluator.ObservationValueCriterionEvaluator;
import org.snomed.cdsservice.service.rules.evaluator.RuleEvaluationContext;
import org.snomed.cdsservice.testsupport.DiagnosticTerminologyFixture;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.SNOMED;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.UCUM;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.condition;
import static org.snomed.cdsservice.service.rules.evaluator.EvaluatorTestSupport.observation;

/**
 * Differential test of rule DM-2024-OGTT-01: the same patient data is evaluated by the TSV rule engine
 * ({@code knowledge-bases/diabetes_*.tsv}) and by the embedded CQL engine running the SMART-style translation
 * ({@code knowledge-bases/smart/diabetes-jm}). For every scenario the two must agree on each criterion's truth value
 * (true / false / unknown), on the rule's truth value, on the card summary, and on whether a card is produced
 * (including suppression when type 2 diabetes, or a subtype, is already recorded).
 */
class SmartDiabetesDifferentialTest {

	private static final String RULE_ID = "DM-2024-OGTT-01";
	private static final String ELEMENTS = "http://snomed.org/fhir/cds-demo/Library/JMDiabetesElements";
	private static final String LOGIC = "http://snomed.org/fhir/cds-demo/Library/JMDiabetesLogic";
	private static final List<String> CRITERIA = List.of(
			"DM_OGTT_2H_GE_11_1", "DM_OGTT_2H_GE_11_1_TWO_OCCASIONS",
			"DM_CLASSIC_HYPERGLYCAEMIC_SYMPTOMS_PRESENT", "DM_HYPERGLYCAEMIC_CRISIS_PRESENT");

	private static final String PATIENT_ID = "p1";
	private static final String OGTT = "113076002";
	private static final String TWO_HOURS = "123030002";
	private static final String POLYURIA = "28442001";

	private static RuleSet diabetes;
	private static Rule rule;
	private static RuleEngine ruleEngine;
	private static PlanDefinitionEngine cqlEngine;
	private static final RuleCardFactory cardFactory = new RuleCardFactory();

	@BeforeAll
	static void setUp() throws Exception {
		EclCodeResolver resolver = new EclCodeResolver(DiagnosticTerminologyFixture.mockClient());

		diabetes = new RuleLoaderService().loadRuleSet("diabetes", "knowledge-bases/diabetes_criteria.tsv", "knowledge-bases/diabetes_rules.tsv");
		rule = diabetes.rules().stream().filter(r -> r.ruleId().equals(RULE_ID)).findFirst().orElseThrow();
		resolver.warmUp(diabetes.criteria().values());
		ObservationCriterionSupport support = new ObservationCriterionSupport(resolver, new CodingBasedSemanticQualifierMatcher());
		ruleEngine = new RuleEngine(new CriterionEvaluatorRegistry(List.of(
				new ObservationValueCriterionEvaluator(support),
				new ObservationComponentValueCriterionEvaluator(support),
				new CodedResourcePresentCriterionEvaluator(resolver),
				new ContextValueCriterionEvaluator())));

		cqlEngine = PlanDefinitionEngine.fromDirectory(FhirContext.forR4Cached(), Path.of("knowledge-bases/smart/diabetes-jm"));
		new EclValueSetPreloader(resolver::expand).preload(cqlEngine);
	}

	private record Scenario(String name, boolean type2DiabetesRecorded, List<Resource> resources) {
	}

	private static List<Scenario> scenarios() {
		return List.of(
				scenario("no data"),
				scenario("single qualifying OGTT, no symptoms",
						ogtt("12.0", "2024-05-01")),
				scenario("single qualifying OGTT with polyuria",
						ogtt("12.0", "2024-05-01"), condition(POLYURIA, "active", "confirmed")),
				scenario("OGTT at the 11.1 boundary with polyuria",
						ogtt("11.1", "2024-05-01"), condition(POLYURIA, "active", "confirmed")),
				scenario("single qualifying OGTT with excessive thirst (symptom subtype)",
						ogtt("12.0", "2024-05-01"), condition("17173007", "active", null)),
				scenario("single qualifying OGTT with diabetic ketoacidosis",
						ogtt("12.0", "2024-05-01"), condition("420422005", "active", "confirmed")),
				scenario("single qualifying OGTT with ketoacidosis due to type 1 diabetes (excluded by ECL MINUS)",
						ogtt("12.0", "2024-05-01"), condition("420270002", "active", "confirmed")),
				scenario("single qualifying OGTT with symptom and crisis",
						ogtt("12.0", "2024-05-01"), condition(POLYURIA, "active", null), condition("441656006", "active", null)),
				scenario("two qualifying OGTTs on different days",
						ogtt("12.0", "2024-05-01"), ogtt("11.5", "2024-05-08")),
				scenario("two qualifying OGTTs on different days with polyuria",
						ogtt("12.0", "2024-05-01"), ogtt("11.5", "2024-05-08"), condition(POLYURIA, "active", "confirmed")),
				scenario("two qualifying OGTTs on the same day",
						ogtt("12.0", "2024-05-01T08:00:00Z"), ogtt("11.5", "2024-05-01T10:00:00Z")),
				scenario("two qualifying OGTTs, one without a date",
						ogtt("12.0", "2024-05-01"), ogtt("11.5", null)),
				scenario("one qualifying OGTT dated only by issued, one by effective",
						issuedOnly(ogtt("12.0", null), "2024-05-01T09:00:00Z"), ogtt("11.5", "2024-05-08")),
				scenario("OGTT below threshold with polyuria",
						ogtt("9.0", "2024-05-01"), condition(POLYURIA, "active", "confirmed")),
				scenario("qualifying and failing OGTTs",
						ogtt("12.0", "2024-05-01"), ogtt("8.0", "2024-05-08")),
				scenario("OGTT without the 2 hours qualifier",
						observation(OGTT, Observation.ObservationStatus.FINAL, "12.0", UCUM, "mmol/L", "2024-05-01")),
				scenario("OGTT with the qualifier on the method",
						withMethod(observation(OGTT, Observation.ObservationStatus.FINAL, "12.0", UCUM, "mmol/L", "2024-05-01")),
						condition(POLYURIA, "active", null)),
				scenario("OGTT in mg/dL",
						withQualifier(observation(OGTT, Observation.ObservationStatus.FINAL, "216", UCUM, "mg/dL", "2024-05-01"))),
				scenario("OGTT without a value in the quantity",
						withQualifier(observation(OGTT, Observation.ObservationStatus.FINAL, null, UCUM, "mmol/L", "2024-05-01"))),
				scenario("OGTT with a string value",
						withStringValue(ogtt("12.0", "2024-05-01"))),
				scenario("preliminary OGTT",
						withQualifier(observation(OGTT, Observation.ObservationStatus.PRELIMINARY, "12.0", UCUM, "mmol/L", "2024-05-01"))),
				scenario("polyuria entered in error",
						ogtt("12.0", "2024-05-01"), condition(POLYURIA, "active", "entered-in-error")),
				scenario("polyuria resolved",
						ogtt("12.0", "2024-05-01"), condition(POLYURIA, "resolved", "confirmed")),
				scenario("polyuria without clinical status",
						ogtt("12.0", "2024-05-01"), condition(POLYURIA, null, null)),
				scenarioWithType2Diabetes("criteria met, type 2 diabetes in remission (a subtype) recorded",
						ogtt("12.0", "2024-05-01"), ogtt("11.5", "2024-05-08"), condition("703138006", "active", "confirmed")),
				scenario("criteria met, type 2 diabetes recorded but inactive",
						ogtt("12.0", "2024-05-01"), ogtt("11.5", "2024-05-08"), condition("44054006", "inactive", "confirmed"))
		);
	}

	@TestFactory
	Stream<DynamicTest> tsvAndCqlEnginesAgree() {
		return scenarios().stream().map(scenario -> DynamicTest.dynamicTest(scenario.name(), () -> {
			List<Resource> data = withPatient(scenario.resources());

			RuleEvaluationResult tsv = ruleEngine.evaluateRule(rule, diabetes.criteria(), new RuleEvaluationContext(null, data, Map.of()));
			Parameters cqlCriteria = cqlEngine.evaluate(ELEMENTS, PATIENT_ID, data, new LinkedHashSet<>(CRITERIA));
			Parameters cqlRule = cqlEngine.evaluate(LOGIC, PATIENT_ID, data, Set.of(RULE_ID, RULE_ID + " Summary"));

			boolean tsvCard = tsv.fired() && !scenario.type2DiabetesRecorded();
			String cqlMessage = guidance((CarePlan) cqlEngine.apply("DM2024OGTT01", PATIENT_ID, data, null));

			List<org.junit.jupiter.api.function.Executable> checks = new ArrayList<>();
			for (int i = 0; i < CRITERIA.size(); i++) {
				String criterion = CRITERIA.get(i);
				TruthValue expected = tsv.criterionResults().stream()
						.filter(result -> result.criterionId().equals(criterion)).findFirst().orElseThrow().truthValue();
				checks.add(() -> assertEquals(expected, truth(cqlCriteria, criterion), criterion));
			}
			checks.add(() -> assertEquals(tsv.truthValue(), truth(cqlRule, RULE_ID), "rule"));
			if (tsv.fired()) {
				checks.add(() -> assertEquals(cardFactory.createCard(tsv).getSummary(), string(cqlRule, RULE_ID + " Summary"), "summary"));
			}
			checks.add(() -> assertEquals(tsvCard, cqlMessage != null, "card produced"));
			if (tsvCard) {
				checks.add(() -> assertEquals(cardFactory.createCard(tsv).getSummary(), cqlMessage.lines().findFirst().orElseThrow(), "card text"));
			}
			assertAll(checks);
		}));
	}

	private static TruthValue truth(Parameters parameters, String name) {
		Parameters.ParametersParameterComponent parameter = parameters.getParameter(name);
		if (parameter == null || !(parameter.getValue() instanceof BooleanType value) || value.getValue() == null) {
			return TruthValue.UNKNOWN;
		}
		return value.booleanValue() ? TruthValue.TRUE : TruthValue.FALSE;
	}

	private static String string(Parameters parameters, String name) {
		Parameters.ParametersParameterComponent parameter = parameters.getParameter(name);
		return parameter == null || !parameter.hasValue() ? null : ((StringType) parameter.getValue()).getValue();
	}

	private static String guidance(CarePlan carePlan) {
		return carePlan.getContained().stream()
				.filter(CommunicationRequest.class::isInstance).map(CommunicationRequest.class::cast)
				.map(request -> request.getPayloadFirstRep().getContentStringType().getValue())
				.findFirst().orElse(null);
	}

	private static Scenario scenario(String name, Resource... resources) {
		return new Scenario(name, false, List.of(resources));
	}

	private static Scenario scenarioWithType2Diabetes(String name, Resource... resources) {
		return new Scenario(name, true, List.of(resources));
	}

	private static List<Resource> withPatient(List<Resource> resources) {
		List<Resource> data = new ArrayList<>();
		data.add(new Patient().setId(PATIENT_ID));
		Reference subject = new Reference("Patient/" + PATIENT_ID);
		for (Resource resource : resources) {
			Resource copy = resource.copy();
			if (copy instanceof Observation observation) {
				observation.setSubject(subject);
			} else if (copy instanceof Condition condition) {
				condition.setSubject(subject);
			}
			data.add(copy);
		}
		return data;
	}

	private static Observation ogtt(String value, String effectiveDate) {
		return withQualifier(observation(OGTT, Observation.ObservationStatus.FINAL, value, UCUM, "mmol/L", effectiveDate));
	}

	private static Observation withQualifier(Observation observation) {
		observation.getCode().addCoding().setSystem(SNOMED).setCode(TWO_HOURS);
		return observation;
	}

	private static Observation withMethod(Observation observation) {
		observation.setMethod(new CodeableConcept().addCoding(new org.hl7.fhir.r4.model.Coding(SNOMED, TWO_HOURS, null)));
		return observation;
	}

	private static Observation withStringValue(Observation observation) {
		observation.setValue(new StringType("12.0 mmol/L"));
		return observation;
	}

	private static Observation issuedOnly(Observation observation, String issued) {
		observation.setIssuedElement(new InstantType(issued));
		return observation;
	}
}
