package org.snomed.cdsservice.rest;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.service.medication.MedicationCombinationRuleLoaderService;
import org.snomed.cdsservice.service.medication.MedicationConditionRuleLoaderService;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.snomed.cdsservice.testsupport.DiagnosticTerminologyFixture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of a PlanDefinition-backed CDS service over HTTP: the WHO SMART Immunizations decision table
 * {@code IMMZ.D2.DT.BCG}, discovered from {@code knowledge-bases/smart} and run by the embedded CQL engine.
 * The CQL evaluates against the current date, so birth dates are relative to today.
 */
@Import(DiagnosticTerminologyFixture.SpringConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanDefinitionCDSServiceApiTest {

	@Autowired
	private MockMvc mockMvc;

	// Medication loaders are mocked so the medication services do not reach the network at startup.
	@MockBean
	private MedicationConditionRuleLoaderService medicationConditionRuleLoaderService;
	@MockBean
	private MedicationCombinationRuleLoaderService medicationCombinationRuleLoaderService;
	@MockBean
	private SnomedMedicationDefinedDailyDoseService snomedMedicationDefinedDailyDoseService;

	@Test
	void discoveryAdvertisesPrefetchDerivedFromDataRequirements() throws Exception {
		mockMvc.perform(get("/cds-services"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.services[?(@.id == 'JMImmunizationSchedule')].hook").value(contains("patient-view")))
				.andExpect(jsonPath("$.services[?(@.id == 'JMImmunizationSchedule')].prefetch.patient").value(contains("Patient/{{context.patientId}}")))
				.andExpect(jsonPath("$.services[?(@.id == 'JMImmunizationSchedule')].prefetch.immunization").value(contains("Immunization?patient={{context.patientId}}")))
				.andExpect(jsonPath("$.services[?(@.id == 'JMImmunizationSchedule')].prefetch.observation").value(contains("Observation?patient={{context.patientId}}")))
				// WHO decision tables composed by the schedule are served through it, not as services of their own.
				.andExpect(jsonPath("$.services[?(@.id == 'IMMZD2DTBCG')]").isEmpty())
				// One PlanDefinition with two hooks is two services; encounter hooks prefetch the encounter itself.
				.andExpect(jsonPath("$.services[?(@.id == 'ANCContactDecisionSupport-encounter-start')].hook").value(contains("encounter-start")))
				.andExpect(jsonPath("$.services[?(@.id == 'ANCContactDecisionSupport-encounter-discharge')].hook").value(contains("encounter-discharge")))
				.andExpect(jsonPath("$.services[?(@.id == 'ANCContactDecisionSupport-encounter-start')].prefetch.encounter").value(contains("Encounter/{{context.encounterId}}")))
				.andExpect(jsonPath("$.services[?(@.id == 'ANCContactDecisionSupport-encounter-start')].prefetch.observation").value(contains("Observation?patient={{context.patientId}}")))
				.andExpect(jsonPath("$.services[?(@.id == 'ANCContactDecisionSupport')]").isEmpty())
				.andExpect(jsonPath("$.services[?(@.id == 'ANCDT01')]").isEmpty());
	}

	@Test
	void diabetesOgttRuleConfirmedOnTwoOccasionsReturnsCard() throws Exception {
		String request = """
				{
				  "hook": "patient-view",
				  "hookInstance": "test-instance",
				  "context": { "patientId": "patient-1" },
				  "prefetch": {
				    "patient": { "resourceType": "Patient", "id": "patient-1" },
				    "observation": {
				      "resourceType": "Bundle",
				      "type": "searchset",
				      "entry": [
				        { "resource": %s },
				        { "resource": %s }
				      ]
				    },
				    "condition": { "resourceType": "Bundle", "type": "searchset" }
				  }
				}
				""".formatted(ogtt("ogtt-1", "12.0", "2024-05-01"), ogtt("ogtt-2", "11.5", "2024-05-08"));

		mockMvc.perform(post("/cds-services/DM2024OGTT01").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards", hasSize(1)))
				.andExpect(jsonPath("$.cards[0].summary").value("Diagnostic OGTT criterion met: "
						+ "Two-hour OGTT plasma glucose at or above 11.1 mmol/L on two separate occasions → Type 2 diabetes mellitus."))
				.andExpect(jsonPath("$.cards[0].indicator").value("info"))
				.andExpect(jsonPath("$.cards[0].source.label").value(startsWith("Guidelines for the Clinical Management of Type 2 Diabetes in Jamaica")))
				.andExpect(jsonPath("$.cards[0].extension['org.snomed.cds-service'].knowledgeArtifact")
						.value("http://snomed.org/fhir/cds-demo/PlanDefinition/DM2024OGTT01|0.1.0"))
				.andExpect(jsonPath("$.cards[0].extension['org.snomed.cds-service'].terminologyVersions")
						.value(contains(DiagnosticTerminologyFixture.CODE_SYSTEM_VERSION)));
	}

	private static String ogtt(String id, String value, String date) {
		return """
				{ "resourceType": "Observation", "id": "%s", "status": "final",
				  "subject": { "reference": "Patient/patient-1" },
				  "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "113076002" },
				                        { "system": "http://snomed.info/sct", "code": "123030002" } ] },
				  "effectiveDateTime": "%s",
				  "valueQuantity": { "value": %s, "system": "http://unitsofmeasure.org", "code": "mmol/L" } }
				""".formatted(id, date, value);
	}

	private static final String BCG_CARD = "$.cards[?(@.source.label == 'IMMZ.D2.DT.BCG')]";

	@Test
	void newbornWithoutBcgIsDue() throws Exception {
		String request = """
				{
				  "hook": "patient-view",
				  "hookInstance": "test-instance",
				  "context": { "patientId": "newborn-1" },
				  "prefetch": {
				    "patient": { "resourceType": "Patient", "id": "newborn-1", "gender": "female", "birthDate": "%s" },
				    "immunization": { "resourceType": "Bundle", "type": "searchset" },
				    "observation": { "resourceType": "Bundle", "type": "searchset" }
				  }
				}
				""".formatted(LocalDate.now().minusDays(2));

		mockMvc.perform(post("/cds-services/JMImmunizationSchedule").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk())
				// One card per WHO decision table of the Jamaica schedule that gives guidance at this age.
				.andExpect(jsonPath("$.cards[*].source.label").value(hasItem("IMMZ.D2.DT.Hepatitis B.Birth dose")))
				.andExpect(jsonPath(BCG_CARD + ".summary").value(contains(startsWith("Should vaccinate client with first BCG dose"))))
				// Proposing a vaccine makes a card actionable (warning); "not due yet" stays informational, and actionable cards come first.
				.andExpect(jsonPath(BCG_CARD + ".indicator").value(contains("warning")))
				.andExpect(jsonPath("$.cards[?(@.source.label == 'IMMZ.D2.DT.Measles.Low transmission')].indicator").value(contains("info")))
				.andExpect(jsonPath("$.cards[0].indicator").value("warning"))
				.andExpect(jsonPath(BCG_CARD + ".referenceMedications[0].coding[*].code").value(hasItem("DE1")))
				// View Source: the rule's page in the WHO implementation guide; links: its CQL and the national schedule.
				.andExpect(jsonPath(BCG_CARD + ".source.url").value(contains("https://smart.who.int/immunizations/PlanDefinition-IMMZD2DTBCG.html")))
				.andExpect(jsonPath(BCG_CARD + ".links[*].url").value(hasItem("https://smart.who.int/immunizations/Library-IMMZD2DTBCGLogic.html")))
				.andExpect(jsonPath(BCG_CARD + ".links[*].label").value(hasItem(startsWith("Jamaica MOHW, EPI Field Guide"))))
				.andExpect(jsonPath(BCG_CARD + ".extension['org.snomed.cds-service'].knowledgeArtifact")
						.value(contains("http://smart.who.int/immunizations/PlanDefinition/IMMZD2DTBCG|1.0.0")))
				.andExpect(jsonPath(BCG_CARD + ".extension['org.snomed.cds-service'].strategy")
						.value(contains("http://snomed.org/fhir/cds-demo/PlanDefinition/JMImmunizationSchedule|0.1.0")))
				// The WHO content enumerates its value sets: nothing was expanded, so no terminology version is claimed.
				.andExpect(jsonPath(BCG_CARD + ".extension['org.snomed.cds-service'].terminologyVersions").isEmpty());
	}

	@Test
	void snomedCodedBcgDoseCompletesTheSchedule() throws Exception {
		String request = """
				{
				  "hook": "patient-view",
				  "hookInstance": "test-instance",
				  "context": { "patientId": "child-1" },
				  "prefetch": {
				    "patient": { "resourceType": "Patient", "id": "child-1", "gender": "male", "birthDate": "%s" },
				    "immunization": {
				      "resourceType": "Bundle",
				      "type": "searchset",
				      "entry": [ { "resource": {
				        "resourceType": "Immunization",
				        "id": "bcg-1",
				        "status": "completed",
				        "vaccineCode": { "coding": [ { "system": "http://snomed.info/sct", "code": "774702006",
				          "display": "Bacillus Calmette-Guerin antigen only product" } ] },
				        "patient": { "reference": "Patient/child-1" },
				        "occurrenceDateTime": "%s",
				        "protocolApplied": [ { "series": "Primary series", "doseNumberString": "1", "seriesDosesString": "1" } ]
				      } } ]
				    },
				    "observation": { "resourceType": "Bundle", "type": "searchset" }
				  }
				}
				""".formatted(LocalDate.now().minusYears(2), LocalDate.now().minusYears(2).plusDays(3));

		mockMvc.perform(post("/cds-services/JMImmunizationSchedule").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath(BCG_CARD + ".summary").value(contains("BCG immunization schedule is complete. One BCG primary series dose was administered.")))
				.andExpect(jsonPath(BCG_CARD + ".referenceMedications").isEmpty());
	}

	private static final String ANC = "http://smart.who.int/anc/CodeSystem/anc-custom-codes";

	@Test
	void ancContactWithCentralCyanosisIsReferred() throws Exception {
		String request = """
				{
				  "hook": "encounter-start",
				  "hookInstance": "test-instance",
				  "context": { "userId": "Practitioner/midwife-1", "patientId": "anc-1", "encounterId": "contact-1" },
				  "prefetch": {
				    "patient": { "resourceType": "Patient", "id": "anc-1", "gender": "female", "birthDate": "1998-03-02" },
				    "encounter": {
				      "resourceType": "Encounter", "id": "contact-1", "status": "in-progress",
				      "extension": [ { "url": "http://smart.who.int/anc/StructureDefinition/contactNumber", "valueInteger": 1 } ],
				      "class": { "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code": "AMB" },
				      "subject": { "reference": "Patient/anc-1" }
				    },
				    "observation": {
				      "resourceType": "Bundle",
				      "type": "searchset",
				      "entry": [
				        { "resource": %s },
				        { "resource": %s }
				      ]
				    }
				  }
				}
				""".formatted(
				// Central cyanosis coded with SNOMED CT, accepted through the curated binding.
				ancObservation("danger-signs", "ANC.B5.DE48", "\"valueCodeableConcept\": { \"coding\": [ { \"system\": \"http://snomed.info/sct\", \"code\": \"95837007\" } ] }"),
				ancObservation("ga", "ANC.B6.DE17", "\"valueQuantity\": { \"value\": 14, \"system\": \"http://unitsofmeasure.org\", \"code\": \"wk\" }"));

		mockMvc.perform(post("/cds-services/ANCContactDecisionSupport-encounter-start").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards[*].summary").value(hasItem("Proceed with ANC contact OR Referral for Central cyanosis")))
				// First contact: HIV (high prevalence setting) and syphilis testing.
				.andExpect(jsonPath("$.cards[*].summary").value(hasItem("Conduct HIV test")))
				.andExpect(jsonPath("$.cards[*].summary").value(hasItem("Conduct syphilis test")))
				// Decisions whose data is missing (e.g. no glucose result) give no card.
				.andExpect(jsonPath("$.cards[?(@.summary =~ /.*diabetes.*/)]").isEmpty())
				.andExpect(jsonPath("$.cards[?(@.summary == 'Conduct HIV test')].detail").value(contains(containsString("**Why:**"))))
				.andExpect(jsonPath("$.cards[?(@.summary == 'Conduct HIV test')].source.url")
						.value(contains("https://build.fhir.org/ig/WorldHealthOrganization/smart-anc/PlanDefinition-ANCDT08.html")))
				.andExpect(jsonPath("$.cards[?(@.summary == 'Conduct HIV test')].extension['org.snomed.cds-service'].strategy")
						.value(contains(startsWith("http://snomed.org/fhir/cds-demo/PlanDefinition/ANCContactDecisionSupport"))))
				// The ANC value sets are bound to SNOMED CT (bindings/), so the cards say which release.
				.andExpect(jsonPath("$.cards[0].extension['org.snomed.cds-service'].terminologyVersions")
						.value(contains("http://snomed.info/sct/900000000000207008/version/20260701")));
	}

	@Test
	void ancObservationsOfAnotherContactAreIgnored() throws Exception {
		String request = """
				{
				  "hook": "encounter-discharge",
				  "hookInstance": "test-instance",
				  "context": { "userId": "Practitioner/midwife-1", "patientId": "anc-1", "encounterId": "contact-2" },
				  "prefetch": {
				    "patient": { "resourceType": "Patient", "id": "anc-1", "gender": "female" },
				    "encounter": { "resourceType": "Bundle", "type": "searchset", "entry": [ { "resource": {
				      "resourceType": "Encounter", "id": "contact-2", "status": "in-progress",
				      "extension": [ { "url": "http://smart.who.int/anc/StructureDefinition/contactNumber", "valueInteger": 2 } ],
				      "class": { "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode", "code": "AMB" },
				      "subject": { "reference": "Patient/anc-1" } } } ] },
				    "observation": { "resourceType": "Bundle", "type": "searchset", "entry": [ { "resource": %s } ] }
				  }
				}
				""".formatted(ancObservation("danger-signs", "ANC.B5.DE48",
				"\"valueCodeableConcept\": { \"coding\": [ { \"system\": \"%s\", \"code\": \"ANC.B5.DE51\" } ] }".formatted(ANC)));

		mockMvc.perform(post("/cds-services/ANCContactDecisionSupport-encounter-discharge").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards[?(@.summary =~ /.*cyanosis.*/)]").isEmpty());
	}

	/** An ANC data element recorded in contact-1. */
	private static String ancObservation(String id, String code, String value) {
		return """
				{ "resourceType": "Observation", "id": "%s", "status": "final",
				  "subject": { "reference": "Patient/anc-1" }, "encounter": { "reference": "Encounter/contact-1" },
				  "code": { "coding": [ { "system": "%s", "code": "%s" } ] },
				  %s }
				""".formatted(id, ANC, code, value);
	}
}
