package org.snomed.cdsservice;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.service.medication.MedicationCombinationRuleLoaderService;
import org.snomed.cdsservice.service.medication.MedicationConditionRuleLoaderService;
import org.snomed.cdsservice.service.medication.dose.SnomedMedicationDefinedDailyDoseService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class CdsServiceApplicationTests {

	@MockBean
	private MedicationConditionRuleLoaderService ruleLoaderService;

	@MockBean
	private MedicationCombinationRuleLoaderService medicationRuleLoaderService;

	@MockBean
	private SnomedMedicationDefinedDailyDoseService definedDailyDoseService;

	@Test
	void contextLoads() {
	}

}
