package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scenarios for the WHO SMART ANC pilot ({@code knowledge-bases/smart/anc-who}): six decision tables evaluated for
 * one antenatal contact. WHO publishes no executable tests for this content, so the oracle is the L2 logic that each
 * PlanDefinition action states in its condition's description (the DAK decision table), not the CQL.
 * <p>
 * Every data element is an Observation coded with an ANC custom code, linked to the current Encounter; the logic only
 * looks at the contact passed in the {@code encounter} parameter.
 * <p>
 * With the curated SNOMED CT binding ({@code bindings/ConceptMap-ANCPilotSNOMEDBinding.json}), the same logic also
 * accepts SNOMED CT-coded data, but only where the binding is exact.
 */
class SmartAncPilotScenariosTest {

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();
	private static final String ANC = "http://smart.who.int/anc/CodeSystem/anc-custom-codes";
	private static final String UCUM = "http://unitsofmeasure.org";
	private static final String SCT = "http://snomed.info/sct";
	private static final String SCT_VERSION = "http://snomed.info/sct/900000000000207008/version/20260701";
	private static final String PATIENT = "anc-patient";
	private static final String ENCOUNTER = "anc-contact";

	private static final CarePlanCardMapper MAPPER = new CarePlanCardMapper(Map.of("http://smart.who.int/anc",
			"https://build.fhir.org/ig/WorldHealthOrganization/smart-anc"));

	private static PlanDefinitionEngine engine;
	private static Set<String> boundValueSets;

	@BeforeAll
	static void loadContent() throws Exception {
		Path content = Path.of("knowledge-bases/smart/anc-who");
		engine = PlanDefinitionEngine.fromDirectory(FHIR_CONTEXT, content);
		boundValueSets = ConceptMapBindings.load(FHIR_CONTEXT, content).applyTo(engine);
	}

	// ANCDT01: danger signs

	@Test
	void noDangerSignsProceedsWithTheContact() {
		assertEquals(Set.of("Proceed with ANC contact"), titles("ANCDT01", 1, coded("ANC.B5.DE48", "ANC.B5.DE49")));
	}

	@Test
	void centralCyanosisIsReferred() {
		assertEquals(Set.of("Proceed with ANC contact OR Referral for Central cyanosis"),
				titles("ANCDT01", 1, coded("ANC.B5.DE48", "ANC.B5.DE51")));
	}

	@Test
	void otherDangerSignsAreReferred() {
		assertEquals(Set.of("Proceed with ANC contact OR Referral"), titles("ANCDT01", 1, coded("ANC.B5.DE48", "ANC.B5.DE50")));
	}

	@Test
	void observationsOfAnotherContactAreIgnored() {
		Observation previousContact = coded("ANC.B5.DE48", "ANC.B5.DE51");
		previousContact.setEncounter(new Reference("Encounter/previous-contact"));
		assertEquals(Set.of(), titles("ANCDT01", 1, previousContact));
	}

	// ANCDT08: HIV testing. ANCConfig sets the population prevalence of HIV in pregnant women to 6 % (a high
	// prevalence setting), so the woman is tested at the first contact and again in the third trimester.

	@Test
	void firstContactConductsHivTest() {
		assertEquals(Set.of("Conduct HIV test"), titles("ANCDT08", 1));
	}

	@Test
	void thirdTrimesterHivNegativeIsTestedAgain() {
		assertEquals(Set.of("Conduct HIV test"),
				titles("ANCDT08", 4, quantity("ANC.B6.DE17", 30, "wk"), coded("ANC.B9.DE45", "ANC.B9.DE47")));
	}

	@Test
	void secondTrimesterFollowUpContactNeedsNoHivTest() {
		assertEquals(Set.of(), titles("ANCDT08", 2, quantity("ANC.B6.DE17", 20, "wk"), coded("ANC.B9.DE45", "ANC.B9.DE47")));
	}

	@Test
	void knownHivPositiveNeedsNoTest() {
		assertEquals(Set.of("HIV test is not required"),
				titles("ANCDT08", 3, quantity("ANC.B6.DE17", 30, "wk"), coded("ANC.B9.DE45", "ANC.B9.DE46")));
	}

	// ANCDT11: syphilis testing

	@Test
	void firstContactConductsSyphilisTest() {
		assertEquals(Set.of("Conduct syphilis test"), titles("ANCDT11", 1, quantity("ANC.B6.DE17", 12, "wk")));
	}

	@Test
	void thirdTrimesterConductsSyphilisTest() {
		assertEquals(Set.of("Conduct syphilis test"), titles("ANCDT11", 4, quantity("ANC.B6.DE17", 30, "wk")));
	}

	@Test
	void secondTrimesterFollowUpContactNeedsNoSyphilisTest() {
		assertEquals(Set.of(), titles("ANCDT11", 2, quantity("ANC.B6.DE17", 20, "wk")));
	}

	@Test
	void syphilisPositiveNeedsNoFurtherTestButTreatment() {
		CarePlan carePlan = apply("ANCDT11", 3, quantity("ANC.B6.DE17", 20, "wk"), coded("ANC.B9.DE110", "ANC.B9.DE111"));
		List<CDSCard> cards = MAPPER.toCards(carePlan, engine.getPlanDefinition("ANCDT11"), null, Set.of());

		assertEquals(List.of("No syphilis test required"), cards.stream().map(CDSCard::getSummary).toList());
		// The decision's other outcome is listed as an action to take.
		assertTrue(cards.get(0).getDetail().contains("- Provide counselling and  treatment"), cards.get(0).getDetail());
	}

	// ANCDT17: hypertension

	@Test
	void severeHypertensionWithProteinuriaIsReferredUrgently() {
		assertTrue(titles("ANCDT17", 2, bloodPressure(165, 165, 100, 100, coded("ANC.B9.DE144", "ANC.B9.DE146")))
				.contains("Refer urgently to a hospital"));
	}

	@Test
	void hypertensionWithSevereSymptomsIsReferredUrgently() {
		assertTrue(titles("ANCDT17", 2, bloodPressure(145, 145, 85, 85, coded("ANC.B8.DE27", "ANC.B8.DE29")))
				.contains("Refer urgently to a hospital"));
	}

	@Test
	void moderateHypertensionWithoutProteinuriaGetsCounselling() {
		assertEquals(Set.of("Conduct hypertension counselling"),
				titles("ANCDT17", 2, bloodPressure(150, 150, 85, 85, coded("ANC.B9.DE144", "ANC.B9.DE145"))));
	}

	@Test
	void normalBloodPressureNeedsNoAction() {
		assertEquals(Set.of(), titles("ANCDT17", 2, bloodPressure(115, 115, 75, 75, coded("ANC.B9.DE144", "ANC.B9.DE145"))));
	}

	// ANCDT23: hyperglycaemia (mg/dL)

	@Test
	void fastingGlucoseInTheGdmRangeGetsGdmCounselling() {
		assertEquals(Set.of("Conduct REQUIRED gestational diabetes mellitus (GDM) counselling"),
				titles("ANCDT23", 3, quantity("ANC.B9.DE163", 100, "mg/dL")));
	}

	@Test
	void fastingGlucoseAtOrAbove126GetsDmCounselling() {
		assertEquals(Set.of("Conduct REQUIRED diabetes mellitus (DM) counselling"),
				titles("ANCDT23", 3, quantity("ANC.B9.DE163", 130, "mg/dL")));
	}

	@Test
	void normalFastingGlucoseNeedsNoCounselling() {
		assertEquals(Set.of(), titles("ANCDT23", 3, quantity("ANC.B9.DE163", 85, "mg/dL")));
	}

	/**
	 * Known deviation of the WHO content: the L2 table gives GDM for a 2-hour OGTT of 153 to 199 mg/dL, the CQL
	 * implements 180 to 199 mg/dL. A 2-hour value of 160 mg/dL therefore gets no card. If WHO fixes the CQL this test
	 * fails, and the expectation should become the L2 one.
	 */
	@Test
	void twoHourOgttBetween153And180DeviatesFromTheL2Table() {
		assertEquals(Set.of(), titles("ANCDT23", 3, quantity("ANC.B9.DE166", 160, "mg/dL")));
		assertEquals(Set.of("Conduct REQUIRED gestational diabetes mellitus (GDM) counselling"),
				titles("ANCDT23", 3, quantity("ANC.B9.DE166", 185, "mg/dL")));
	}

	// ANCDT25: anaemia (Hb in g/L)

	@Test
	void lowHaemoglobinInTheThirdTrimesterGetsAnaemiaCounselling() {
		assertTrue(titles("ANCDT25", 5, quantity("ANC.B6.DE17", 30, "wk"), quantity("ANC.B9.DE186", 100, "g/L"))
				.contains("Conduct REQUIRED anaemia counselling"));
	}

	@Test
	void secondTrimesterThresholdIs105() {
		assertFalse(titles("ANCDT25", 3, quantity("ANC.B6.DE17", 20, "wk"), quantity("ANC.B9.DE186", 107, "g/L"))
				.contains("Conduct REQUIRED anaemia counselling"));
		assertTrue(titles("ANCDT25", 3, quantity("ANC.B6.DE17", 20, "wk"), quantity("ANC.B9.DE186", 100, "g/L"))
				.contains("Conduct REQUIRED anaemia counselling"));
	}

	@Test
	void pallorWithoutHaemoglobinGetsAnaemiaCounselling() {
		Observation pallor = observation("ANC.B8.DE38", new BooleanType(true));
		assertTrue(titles("ANCDT25", 3, quantity("ANC.B6.DE17", 20, "wk"), pallor).contains("Conduct REQUIRED anaemia counselling"));
	}


	// SNOMED CT-coded data, through the curated binding

	@Test
	void bindingAddsSnomedCodesToTheAncValueSets() {
		assertTrue(boundValueSets.contains("http://smart.who.int/anc/ValueSet/anc-b9-de163"), boundValueSets.toString());
		assertTrue(engine.getTerminologyVersions().contains(SCT_VERSION), engine.getTerminologyVersions().toString());
	}

	@Test
	void snomedCentralCyanosisIsReferred() {
		assertEquals(Set.of("Proceed with ANC contact OR Referral for Central cyanosis"),
				titles("ANCDT01", 1, coded("ANC.B5.DE48", SCT, "95837007")));
	}

	@Test
	void snomedHeadacheIsNotTheDangerSignSevereHeadache() {
		// WHO maps "Severe headache" to Headache; the binding does not, or any headache would mean referral.
		assertEquals(Set.of(), titles("ANCDT01", 1, coded("ANC.B5.DE48", SCT, "25064002")));
	}

	@Test
	void snomedFastingPlasmaGlucoseGetsGdmCounselling() {
		// WHO maps it to 67096006, which does not exist.
		assertEquals(Set.of("Conduct REQUIRED gestational diabetes mellitus (GDM) counselling"),
				titles("ANCDT23", 3, measured(SCT, "167096006", 100, "mg/dL")));
	}

	@Test
	void snomedTwoHourOgttGetsDmCounselling() {
		// WHO maps it to the qualifier "2 hours".
		assertEquals(Set.of("Conduct REQUIRED diabetes mellitus (DM) counselling"),
				titles("ANCDT23", 3, measured(SCT, "49167009", 210, "mg/dL")));
	}

	@Test
	void snomedHaemoglobinAndGestationalAgeGetAnaemiaCounselling() {
		// WHO maps haemoglobin to an inactive navigational concept.
		assertTrue(titles("ANCDT25", 5, measured(SCT, "57036006", 30, "wk"), measured(SCT, "271026005", 100, "g/L"))
				.contains("Conduct REQUIRED anaemia counselling"));
	}

	@Test
	void snomedHivPositiveNeedsNoTest() {
		// WHO has no map for HIV status.
		assertEquals(Set.of("HIV test is not required"),
				titles("ANCDT08", 3, measured(SCT, "57036006", 30, "wk"), coded(SCT, "278977008", SCT, "165816005")));
	}

	@Test
	void snomedSyphilisDiagnosisNeedsNoTest() {
		assertEquals(Set.of("No syphilis test required"),
				titles("ANCDT11", 3, quantity("ANC.B6.DE17", 20, "wk"), coded("ANC.B9.DE110", SCT, "76272004")));
	}

	@Test
	void snomedUrineProteinNotDetectedWithModerateHypertensionGetsCounselling() {
		// WHO maps the dipstick grades to Mild, Moderate, Severe, Deep and None.
		assertEquals(Set.of("Conduct hypertension counselling"),
				titles("ANCDT17", 2, bloodPressure(150, 150, 85, 85, coded(SCT, "271346009", SCT, "167273002"))));
	}

	// Cards

	@Test
	void actionsBecomeCardsCitingTheL2Logic() {
		CarePlan carePlan = apply("ANCDT01", 1, coded("ANC.B5.DE48", "ANC.B5.DE51"));
		PlanDefinition rule = engine.getPlanDefinition("ANCDT01");

		List<CDSCard> cards = MAPPER.toCards(carePlan, rule, null, Set.of());

		assertEquals(1, cards.size());
		CDSCard card = cards.get(0);
		assertEquals("Proceed with ANC contact OR Referral for Central cyanosis", card.getSummary());
		assertTrue(card.getDetail().contains("**Why:**"), card.getDetail());
		assertTrue(card.getDetail().contains("Central cyanosis"), card.getDetail());
		assertEquals(CDSIndicator.info, card.getIndicator());
	}

	@Test
	void strategyComposesThePilotTables() {
		assertEquals(List.of("ANCDT01", "ANCDT08", "ANCDT11", "ANCDT17", "ANCDT23", "ANCDT25"),
				engine.composedPlanDefinitions("ANCContactDecisionSupport").stream().map(PlanDefinition::getIdPart).sorted().toList());
	}

	/** The summaries of the cards the rule produces, i.e. the titles of the actions that applied. */
	private static Set<String> titles(String planDefinitionId, int contactNumber, Observation... observations) {
		Set<String> titles = new LinkedHashSet<>();
		MAPPER.toCards(apply(planDefinitionId, contactNumber, observations), engine.getPlanDefinition(planDefinitionId), null, Set.of())
				.forEach(card -> titles.add(card.getSummary()));
		return titles;
	}

	private static CarePlan apply(String planDefinitionId, int contactNumber, Observation... observations) {
		List<Resource> data = new ArrayList<>();
		Patient patient = new Patient();
		patient.setId(PATIENT);
		patient.setGender(org.hl7.fhir.r4.model.Enumerations.AdministrativeGender.FEMALE);
		data.add(patient);
		Encounter encounter = new Encounter();
		encounter.setId(ENCOUNTER);
		encounter.setStatus(Encounter.EncounterStatus.INPROGRESS);
		encounter.setSubject(new Reference("Patient/" + PATIENT));
		encounter.addExtension("http://smart.who.int/anc/StructureDefinition/contactNumber", new IntegerType(contactNumber));
		data.add(encounter);
		int i = 0;
		for (Observation observation : observations) {
			observation.setId("obs-" + i++);
			data.add(observation);
		}
		Parameters parameters = new Parameters();
		parameters.addParameter().setName(PlanDefinitionCDSService.ENCOUNTER_PARAMETER).setValue(new StringType(ENCOUNTER));
		return (CarePlan) engine.apply(planDefinitionId, PATIENT, data, parameters);
	}

	private static Observation coded(String element, String value) {
		return coded(ANC, element, ANC, value);
	}

	/** An ANC question answered with a code of another system. */
	private static Observation coded(String element, String valueSystem, String value) {
		return coded(ANC, element, valueSystem, value);
	}

	private static Observation coded(String elementSystem, String element, String valueSystem, String value) {
		return observation(elementSystem, element, new CodeableConcept().addCoding(new org.hl7.fhir.r4.model.Coding(valueSystem, value, null)));
	}

	private static Observation quantity(String element, double value, String unit) {
		return measured(ANC, element, value, unit);
	}

	private static Observation measured(String elementSystem, String element, double value, String unit) {
		return observation(elementSystem, element, new Quantity().setValue(value).setSystem(UCUM).setCode(unit).setUnit(unit));
	}

	/** Blood pressure measured twice in the contact (mm[Hg]), plus other observations. */
	private static Observation[] bloodPressure(double systolic, double repeatSystolic, double diastolic, double repeatDiastolic,
			Observation... others) {
		List<Observation> observations = new ArrayList<>(List.of(
				quantity("ANC.B8.DE17", systolic, "mm[Hg]"), quantity("ANC.B8.DE20", repeatSystolic, "mm[Hg]"),
				quantity("ANC.B8.DE19", diastolic, "mm[Hg]"), quantity("ANC.B8.DE21", repeatDiastolic, "mm[Hg]")));
		observations.addAll(List.of(others));
		return observations.toArray(Observation[]::new);
	}

	private static Observation observation(String element, Type value) {
		return observation(ANC, element, value);
	}

	private static Observation observation(String elementSystem, String element, Type value) {
		Observation observation = new Observation();
		observation.setStatus(Observation.ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem(elementSystem).setCode(element);
		observation.setSubject(new Reference("Patient/" + PATIENT));
		observation.setEncounter(new Reference("Encounter/" + ENCOUNTER));
		observation.setValue(value);
		return observation;
	}
}
