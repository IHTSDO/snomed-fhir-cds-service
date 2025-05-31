package org.snomed.cdsservice.service.medication;

import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.model.MedicationConditionCDSTrigger;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class MedicationConditionRuleLoaderServiceTest {

	@Autowired
	private MedicationConditionRuleLoaderService service;

	@MockBean
	private FHIRTerminologyServerClient mockTsClient;

	@BeforeEach
	void setUp() {
		// Set the test TSV file path
		ReflectionTestUtils.setField(service, "tsvPath", "src/test/resources/test-rules/CDS_Medication-Condition_Cards.tsv");
		
		// Mock the terminology server responses for medication and condition
		when(mockTsClient.expandValueSet(eq("http://snomed.info/sct?fhir_vs=isa/33664007"))).thenReturn(List.of(
			new Coding("http://snomed.info/sct", "33664007", "Acetazolamide")
		));
		
		when(mockTsClient.expandValueSet(eq("http://snomed.info/sct?fhir_vs=isa/18104000"))).thenReturn(List.of(
			new Coding("http://snomed.info/sct", "18104000", "Hyperchloremic acidosis")
		));

		when(mockTsClient.expandValueSet(eq("http://snomed.info/sct?fhir_vs=isa/108600003"))).thenReturn(List.of(
			new Coding("http://snomed.info/sct", "108600003", "Atorvastatin")
		));

		when(mockTsClient.expandValueSet(eq("http://snomed.info/sct?fhir_vs=isa/235856003"))).thenReturn(List.of(
			new Coding("http://snomed.info/sct", "235856003", "Disease of liver"),
			new Coding("http://snomed.info/sct", "197321007", "Steatosis of liver")
		));
	}

	@Test
	void shouldLoadTriggersFromTSV() throws ServiceException {
		// When
		List<CDSTrigger> triggers = service.loadTriggers();

		// Then
		assertNotNull(triggers);
		assertFalse(triggers.isEmpty());

		// Verify first trigger
		MedicationConditionCDSTrigger firstTrigger = (MedicationConditionCDSTrigger) triggers.get(0);
		assertEquals("Atorvastatin", firstTrigger.getMedicationLabel());
		assertEquals("Disease of liver", firstTrigger.getConditionLabel());
		
		CDSCard card = firstTrigger.getCard();
		assertEquals("7b544b67-fbc7-48af-af95-ddaee09e836b", card.getUuid());
		assertEquals(CDSIndicator.warning, card.getIndicator());
		assertEquals("Contraindication: {{ActualMedication}} with patient condition {{ActualCondition}}.", card.getSummary());
		assertEquals("The use of {{RuleMedication}} is contraindicated when the patient has {{RuleCondition}}.", card.getDetail());
		assertEquals("Wikipedia", card.getSource().getLabel());
		assertEquals("https://en.wikipedia.org/wiki/Atorvastatin#Contraindications", card.getSource().getUrl());
	}

	@Test
	void shouldHandleEmptyOrInvalidRows() throws ServiceException {
		// When
		List<CDSTrigger> triggers = service.loadTriggers();

		// Then
		assertNotNull(triggers);
		// Verify that only valid rows are processed
		for (CDSTrigger trigger : triggers) {
			assertNotNull(trigger.getMedicationLabel());
			assertNotNull(trigger.getConditionLabel());
			assertNotNull(trigger.getCard().getUuid());
			assertNotNull(trigger.getCard().getIndicator());
		}
	}

	@Test
	void shouldProcessSNOMEDCodesCorrectly() throws ServiceException {
		// When
		List<CDSTrigger> triggers = service.loadTriggers();

		// Then
		assertNotNull(triggers);
		assertFalse(triggers.isEmpty());

		// Verify SNOMED code processing
		MedicationConditionCDSTrigger trigger = (MedicationConditionCDSTrigger) triggers.get(0);
		Collection<Coding> medicationCodings = trigger.getMedicationCodings();
		Collection<Coding> conditionCodings = trigger.getConditionCodings();

		assertFalse(medicationCodings.isEmpty());
		assertFalse(conditionCodings.isEmpty());

		// Verify medication coding values
		Coding medicationCoding = medicationCodings.iterator().next();
		assertEquals("http://snomed.info/sct", medicationCoding.getSystem());
		assertEquals("108600003", medicationCoding.getCode());
		assertEquals("Atorvastatin", medicationCoding.getDisplay());

		// Verify condition coding values
		assertEquals(2, conditionCodings.size());
		boolean hasLiverDisease = false;
		boolean hasSteatosis = false;
		for (Coding conditionCoding : conditionCodings) {
			assertEquals("http://snomed.info/sct", conditionCoding.getSystem());
			if (conditionCoding.getCode().equals("235856003")) {
				assertEquals("Disease of liver", conditionCoding.getDisplay());
				hasLiverDisease = true;
			} else if (conditionCoding.getCode().equals("197321007")) {
				assertEquals("Steatosis of liver", conditionCoding.getDisplay());
				hasSteatosis = true;
			}
		}
		assertTrue(hasLiverDisease, "Should contain Disease of liver coding");
		assertTrue(hasSteatosis, "Should contain Steatosis of liver coding");
	}
}
