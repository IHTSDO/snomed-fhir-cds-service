package org.snomed.cdsservice;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.service.MedicationConditionRuleLoaderService;
import org.snomed.cdsservice.service.MedicationRuleLoaderService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class CdsServiceApplicationTests {

	@MockBean
	private MedicationConditionRuleLoaderService ruleLoaderService;

	@MockBean
	private MedicationRuleLoaderService medicationRuleLoaderService;

	@Test
	void contextLoads() {
	}

}
