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
				.andExpect(jsonPath("$.services[?(@.id == 'IMMZD2DTBCG')].hook").value(contains("patient-view")))
				.andExpect(jsonPath("$.services[?(@.id == 'IMMZD2DTBCG')].prefetch.patient").value(contains("Patient/{{context.patientId}}")))
				.andExpect(jsonPath("$.services[?(@.id == 'IMMZD2DTBCG')].prefetch.immunization").value(contains("Immunization?patient={{context.patientId}}")))
				.andExpect(jsonPath("$.services[?(@.id == 'IMMZD2DTBCG')].prefetch.observation").value(contains("Observation?patient={{context.patientId}}")));
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

		mockMvc.perform(post("/cds-services/IMMZD2DTBCG").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards", hasSize(1)))
				.andExpect(jsonPath("$.cards[0].summary").value(startsWith("Should vaccinate client with first BCG dose")))
				.andExpect(jsonPath("$.cards[0].indicator").value("info"))
				.andExpect(jsonPath("$.cards[0].source.label").value("IMMZ.D2.DT.BCG"))
				.andExpect(jsonPath("$.cards[0].referenceMedications[0].coding[*].code").value(hasItem("DE1")))
				.andExpect(jsonPath("$.cards[0].extension['org.snomed.cds-service'].knowledgeArtifact")
						.value("http://smart.who.int/immunizations/PlanDefinition/IMMZD2DTBCG|1.0.0"))
				// The WHO content enumerates its value sets: nothing was expanded, so no terminology version is claimed.
				.andExpect(jsonPath("$.cards[0].extension['org.snomed.cds-service'].terminologyVersions").doesNotExist());
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

		mockMvc.perform(post("/cds-services/IMMZD2DTBCG").contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards", hasSize(1)))
				.andExpect(jsonPath("$.cards[0].summary").value("BCG immunization schedule is complete. One BCG primary series dose was administered."))
				.andExpect(jsonPath("$.cards[0].referenceMedications").doesNotExist());
	}
}
