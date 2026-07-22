package org.snomed.cdsservice.rest;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.service.medication.MedicationCombinationRuleLoaderService;
import org.snomed.cdsservice.service.medication.MedicationConditionRuleLoaderService;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ServicesDiscoveryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private MedicationConditionRuleLoaderService ruleLoaderService;

	@MockBean
	private MedicationCombinationRuleLoaderService medicationRuleLoaderService;

	@MockBean
	private SnomedMedicationDefinedDailyDoseService definedDailyDoseService;

	@Test
	void shouldPublishStandardClinicalHooks() throws Exception {
		mockMvc.perform(get("/cds-services"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.services[*].id").value(hasItems(
						"medication-order-select",
						"medication-order-sign",
						"problem-list-item-create-medication-check",
						"patient-view-medication-summary-check",
						"allergyintolerance-create-medication-check"
				)))
				.andExpect(jsonPath("$.services[*].hook").value(hasItems(
						"order-select",
						"order-sign",
						"problem-list-item-create",
						"patient-view",
						"allergyintolerance-create"
				)));
	}

	@Test
	void shouldPublishDataDrivenDiagnosticServices() throws Exception {
		// The diabetes and hypertension domains are discovered from their TSV files and registered dynamically.
		mockMvc.perform(get("/cds-services"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.services[*].id").value(hasItems(
						"diagnostic-support-diabetes",
						"diagnostic-support-hypertension"
				)));
	}
}
