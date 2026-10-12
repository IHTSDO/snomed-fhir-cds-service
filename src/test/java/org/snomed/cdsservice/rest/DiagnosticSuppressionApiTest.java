package org.snomed.cdsservice.rest;

import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.service.medication.MedicationCombinationRuleLoaderService;
import org.snomed.cdsservice.service.medication.MedicationConditionRuleLoaderService;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import org.snomed.cdsservice.testsupport.DiagnosticTerminologyFixture;
import org.springframework.context.annotation.Import;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies subtype-aware suppression: a fired diagnostic rule is suppressed when the patient already
 * carries the outcome diagnosis or any of its subtypes as an active condition. The terminology server is
 * mocked so the {@code << 44054006} expansion is deterministic and no network is used.
 */
@Import(DiagnosticTerminologyFixture.SpringConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
class DiagnosticSuppressionApiTest {

	private static final String SNOMED = "http://snomed.info/sct";
	private static final String T2DM = "44054006"; // Type 2 diabetes mellitus (the rule outcome)
	private static final String T2DM_SUBTYPE = "237599002"; // Insulin treated type 2 diabetes mellitus (a subtype)
	private static final String UNRELATED = "29951006"; // Chronic laryngitis

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private MedicationConditionRuleLoaderService medicationConditionRuleLoaderService;
	@MockBean
	private MedicationCombinationRuleLoaderService medicationCombinationRuleLoaderService;
	@MockBean
	private SnomedMedicationDefinedDailyDoseService snomedMedicationDefinedDailyDoseService;
	@MockBean
	private FHIRTerminologyServerClient terminologyServerClient;

	@BeforeEach
	void stubExpansion() {
		// << 44054006 expands to the concept itself plus a subtype; anything else expands to nothing.
		when(terminologyServerClient.expandValueSet(anyString())).thenAnswer(invocation -> {
			String url = invocation.getArgument(0);
			if (url.contains(T2DM)) {
				return List.of(coding(T2DM), coding(T2DM_SUBTYPE));
			}
			return List.of();
		});
	}

	@Test
	void exactOutcomeConditionSuppresses() throws Exception {
		postFastingGlucoseWithCondition(T2DM)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards", hasSize(0)));
	}

	@Test
	void subtypeOfOutcomeConditionSuppresses() throws Exception {
		postFastingGlucoseWithCondition(T2DM_SUBTYPE)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards", hasSize(0)));
	}

	@Test
	void unrelatedConditionDoesNotSuppress() throws Exception {
		postFastingGlucoseWithCondition(UNRELATED)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards", hasSize(1)));
	}

	@Test
	void severeBloodPressureIsNotSuppressedByKnownHypertension() throws Exception {
		// The guideline gives actions for known hypertensives too, so the severe rule never suppresses.
		String body = """
				{
				  "hook": "patient-view",
				  "context": { "patientId": "p" },
				  "prefetch": {
				    "conditions": { "resourceType": "Bundle", "type": "searchset", "entry": [
				      { "resource": { "resourceType": "Condition",
				        "clinicalStatus": { "coding": [ { "system": "http://terminology.hl7.org/CodeSystem/condition-clinical", "code": "active" } ] },
				        "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "38341003" } ] } } }
				    ] },
				    "observations": { "resourceType": "Bundle", "type": "searchset", "entry": [
				      { "resource": { "resourceType": "Observation", "status": "final",
				        "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "75367002" } ] },
				        "component": [
				          { "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "271649006" } ] },
				            "valueQuantity": { "value": 190, "system": "http://unitsofmeasure.org", "code": "mm[Hg]" } },
				          { "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "271650006" } ] },
				            "valueQuantity": { "value": 100, "system": "http://unitsofmeasure.org", "code": "mm[Hg]" } } ] } }
				    ] }
				  }
				}
				""";
		mockMvc.perform(post("/cds-services/diagnostic-support-hypertension")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards", hasSize(1)))
				.andExpect(jsonPath("$.cards[0].indicator").value("critical"));
	}

	private org.springframework.test.web.servlet.ResultActions postFastingGlucoseWithCondition(String conditionCode) throws Exception {
		String body = """
				{
				  "hook": "patient-view",
				  "context": { "patientId": "p" },
				  "prefetch": {
				    "conditions": { "resourceType": "Bundle", "type": "searchset", "entry": [
				      { "resource": { "resourceType": "Condition",
				        "clinicalStatus": { "coding": [ { "system": "http://terminology.hl7.org/CodeSystem/condition-clinical", "code": "active" } ] },
				        "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "%s" } ] } } }
				    ] },
				    "observations": { "resourceType": "Bundle", "type": "searchset", "entry": [
				      { "resource": { "resourceType": "Observation", "status": "final",
				        "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "271062006" } ] },
				        "valueQuantity": { "value": 7.4, "system": "http://unitsofmeasure.org", "code": "mmol/L" } } }
				    ] }
				  }
				}
				""".formatted(conditionCode);
		return mockMvc.perform(post("/cds-services/diagnostic-support-diabetes")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private static Coding coding(String code) {
		return new Coding().setSystem(SNOMED).setCode(code);
	}
}
