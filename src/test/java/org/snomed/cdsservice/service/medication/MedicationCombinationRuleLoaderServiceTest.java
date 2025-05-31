package org.snomed.cdsservice.service.medication;

import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.model.MedicationInterationCDSTrigger;
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
class MedicationCombinationRuleLoaderServiceTest {

	@Autowired
	private MedicationCombinationRuleLoaderService service;

	@MockBean
	private FHIRTerminologyServerClient mockTsClient;

	@BeforeEach
	void setUp() {
		// Set the test TSV file path
		ReflectionTestUtils.setField(service, "tsvPath", "src/test/resources/test-rules/CDS_Medication-Medication_Cards.tsv");
		
		// Mock the terminology server responses for each medication
		when(mockTsClient.expandValueSet(eq("http://snomed.info/sct?fhir_vs=isa/33664007"))).thenReturn(List.of(
			new Coding("http://snomed.info/sct", "33664007", "Acetazolamide")
		));
		
		when(mockTsClient.expandValueSet(eq("http://snomed.info/sct?fhir_vs=isa/96119002"))).thenReturn(List.of(
			new Coding("http://snomed.info/sct", "96119002", "Albendazole")
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
		MedicationInterationCDSTrigger firstTrigger = (MedicationInterationCDSTrigger) triggers.get(0);
		assertEquals("Acetazolamide", firstTrigger.getMedicationLabel());
		assertEquals("Albendazole", firstTrigger.getMedication2Label());
		
		CDSCard card = firstTrigger.getCard();
		assertEquals("7db8e2f9-0baa-477e-8682-24bdffbd3fda", card.getUuid());
		assertEquals(CDSIndicator.warning, card.getIndicator());
		assertEquals("Contraindication of drug-drug interaction: {{ActualMedication1}} with {{ActualMedication2}}.", card.getSummary());
		assertEquals("The use of {{RuleMedication1}} is contraindicated with {{RuleMedication2}}. The metabolism of Albendazole can be decreased when combined with Acetazolamide.", card.getDetail());
		assertEquals("Drug Bank", card.getSource().getLabel());
		assertEquals("https://go.drugbank.com/drugs/DB00819#drug-interactions", card.getSource().getUrl());
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
			assertNotNull(trigger.getMedication2Label());
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
		MedicationInterationCDSTrigger trigger = (MedicationInterationCDSTrigger) triggers.get(0);
		Collection<Coding> medication1Codings = trigger.getMedicationCodings();
		Collection<Coding> medication2Codings = trigger.getMedication2Codings();

		assertFalse(medication1Codings.isEmpty());
		assertFalse(medication2Codings.isEmpty());

		// Verify coding values
		Coding medication1Coding = medication1Codings.iterator().next();
		assertEquals("http://snomed.info/sct", medication1Coding.getSystem());
		assertEquals("33664007", medication1Coding.getCode());
		assertEquals("Acetazolamide", medication1Coding.getDisplay());

		Coding medication2Coding = medication2Codings.iterator().next();
		assertEquals("http://snomed.info/sct", medication2Coding.getSystem());
		assertEquals("96119002", medication2Coding.getCode());
		assertEquals("Albendazole", medication2Coding.getDisplay());
	}
}
