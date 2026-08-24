package org.snomed.cdsservice.rest;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.service.medication.MedicationCombinationRuleLoaderService;
import org.snomed.cdsservice.service.medication.MedicationConditionRuleLoaderService;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test of a dynamically-registered diagnostic CDS service over HTTP: a patient-view request
 * with a prefetched fasting-glucose Observation must return a diagnostic card. Uses only locally
 * resolvable ECL, so no terminology server is contacted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DiagnosticCDSServiceApiTest {

	@Autowired
	private MockMvc mockMvc;

	// Medication loaders are mocked so the medication services do not reach the network at startup.
	@MockBean
	private MedicationConditionRuleLoaderService medicationConditionRuleLoaderService;
	@MockBean
	private MedicationCombinationRuleLoaderService medicationCombinationRuleLoaderService;
	@MockBean
	private SnomedMedicationDefinedDailyDoseService snomedMedicationDefinedDailyDoseService;

	private static final String FASTING_GLUCOSE_REQUEST = """
			{
			  "hook": "patient-view",
			  "hookInstance": "test-instance",
			  "context": { "patientId": "patient-1" },
			  "prefetch": {
			    "patient": { "resourceType": "Patient", "id": "patient-1" },
			    "observations": {
			      "resourceType": "Bundle",
			      "type": "searchset",
			      "entry": [
			        {
			          "resource": {
			            "resourceType": "Observation",
			            "status": "final",
			            "code": { "coding": [ { "system": "http://snomed.info/sct", "code": "271062006" } ] },
			            "valueQuantity": { "value": 7.4, "system": "http://unitsofmeasure.org", "code": "mmol/L" }
			          }
			        }
			      ]
			    }
			  }
			}
			""";

	@Test
	void fastingGlucoseRequestReturnsDiagnosticCard() throws Exception {
		mockMvc.perform(post("/cds-services/diagnostic-support-diabetes")
						.contentType(MediaType.APPLICATION_JSON)
						.content(FASTING_GLUCOSE_REQUEST))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cards[*].summary").value(hasItem(containsString("Type 2 diabetes mellitus"))))
				.andExpect(jsonPath("$.cards[*].indicator").value(hasItem("info")));
	}
}
