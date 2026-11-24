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
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With the local profile's CORS settings, a frontend served from a public site can call the service on localhost,
 * including Chromium's Private Network Access preflight.
 */
@Import(DiagnosticTerminologyFixture.SpringConfig.class)
@SpringBootTest(properties = {
		"app.cors.enabled=true",
		"app.cors.allowed-origin-patterns=*",
		"app.cors.allow-private-network=true"
})
@AutoConfigureMockMvc
class CorsConfigTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private MedicationConditionRuleLoaderService medicationConditionRuleLoaderService;
	@MockBean
	private MedicationCombinationRuleLoaderService medicationCombinationRuleLoaderService;
	@MockBean
	private SnomedMedicationDefinedDailyDoseService snomedMedicationDefinedDailyDoseService;

	@Test
	void preflightFromAPublicSiteToAPrivateNetworkIsAllowed() throws Exception {
		mockMvc.perform(options("/cds-services/DM2024OGTT01")
						.header("Origin", "https://frontend.example.org")
						.header("Access-Control-Request-Method", "POST")
						.header("Access-Control-Request-Headers", "content-type")
						.header("Access-Control-Request-Private-Network", "true"))
				.andExpect(status().isOk())
				.andExpect(header().string("Access-Control-Allow-Origin", "https://frontend.example.org"))
				.andExpect(header().string("Access-Control-Allow-Private-Network", "true"));
	}
}
