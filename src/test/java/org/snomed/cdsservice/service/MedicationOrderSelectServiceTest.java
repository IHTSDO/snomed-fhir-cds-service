package org.snomed.cdsservice.service;

import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSCoding;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSReference;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.model.CDSTrigger;
import org.snomed.cdsservice.model.MedicationConditionCDSTrigger;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class MedicationOrderSelectServiceTest {

	@MockBean
	private MedicationConditionRuleLoaderService ruleLoaderService;

	@MockBean
	private MedicationCombinationRuleLoaderService medicationRuleLoaderService;

	@Autowired
	private MedicationOrderSelectService service;

	@BeforeEach
	void setMockOutput() {
		CDSTrigger trigger = new MedicationConditionCDSTrigger(
				"Atorvastatin",
				Collections.singleton(new Coding("http://snomed.info/sct", "1145419005", null)),
				"Disease of liver",
				List.of(
						new Coding("http://snomed.info/sct", "235856003", "Disease of liver"),
						new Coding("http://snomed.info/sct", "197321007", "Steatosis of liver")
				),
				new CDSCard(
						"c2f4ca5c-96a0-49c5-bb80-cbfcc015abfd",
						"Contraindication: {{ActualMedication}} with patient condition {{ActualCondition}}.",
						"The use of {{RuleMedication}} is contraindicated when the patient has {{RuleCondition}}.",
						CDSIndicator.warning,
						new CDSSource("Wikipedia"),
						Stream.of(new CDSReference(Collections.singletonList(new CDSCoding("http://snomed.info/sct", "1145419005")))).collect(Collectors.toList()),
						new CDSReference(Collections.singletonList(new CDSCoding("http://snomed.info/sct", "197321007")))));
		service.setMedicationOrderSelectTriggers(List.of(trigger));
	}

	@Test
	public void shouldReturnAlert_WhenDrugAndConditionIsContraindicated() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundle.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());

		CDSCard cdsCard = cards.get(0);
		assertEquals("Contraindication: \"Atorvastatin (as atorvastatin calcium) 10 mg oral tablet\" with patient condition \"Steatosis of liver\".", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertEquals("The use of Atorvastatin is contraindicated when the patient has Disease of liver.", cdsCard.getDetail());
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals("197321007", cdsCard.getReferenceCondition().getCoding().get(0).getCode());
	}


	@Test
	public void shouldReturnOverDoseWarningAlert_WhenPrescribedDailyDoseExceedsMaximumThresholdFactor() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithWarningOverDose.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 6 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 6.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
	}

	@Test
	public void shouldReturnOverDoseInfoAlert_WhenPrescribedDailyDoseExceedsAcceptableThresholdFactor() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithInfoOverDose.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 2.5 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.info, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 2.50 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
	}
	@Test
	public void shouldNotReturnOverDoseAlert_WhenPrescribedDailyDoseIsWithinAcceptableThresholdFactor() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithNoOverDose.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());
	}
	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForFrequencyPeriodUnitInDays() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithFrequencyPeriodUnitInDays.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 12 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 12.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
	}
	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForFrequencyPeriodUnitInHours() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithFrequencyPeriodUnitInHours.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 12 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 12.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
	}
	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForFrequencyPeriodUnitInWeeks() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithFrequencyPeriodUnitInWeeks.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 5 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 5.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
	}
	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForFrequencyPeriodUnitInMonths() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithFrequencyPeriodUnitInMonths.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard = cards.get(1);
		assertEquals("The amount of Atorvastatin prescribed is 5 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 5.00 times the average daily dose."));
		assertEquals("1145419005", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C10AA05"));
	}


	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForMultipleDrugs_WithDifferentDosageUnits() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithDosageAndUnits.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());
		CDSCard cdsCard1 = cards.get(0);
		assertEquals("The amount of Ramipril prescribed is 96 times the average daily dose.", cdsCard1.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard1.getIndicator());
		assertTrue( cdsCard1.getDetail().contains("Conclusion : Combined prescribed amount is 96.00 times the average daily dose."));
		assertEquals("408051007", cdsCard1.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard1.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=C09AA05"));

		CDSCard cdsCard2 = cards.get(1);
		assertEquals("The amount of Ranitidine prescribed is 40 times the average daily dose.", cdsCard2.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard2.getIndicator());
		assertTrue( cdsCard2.getDetail().contains("Conclusion : Combined prescribed amount is 40.00 times the average daily dose."));
		assertEquals("782087002", cdsCard2.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertTrue(cdsCard2.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=A02BA02"));
	}

	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForMultipleDrugsHavingSameSubstance_WithDifferentRouteOfAdministration() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithDifferentManufacturedDosageFormAndDifferentRouteOfAdministration.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());

		CDSCard cdsCard = cards.get(0);
		assertEquals("The amount of Ranitidine prescribed is 64 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 64.00 times the average daily dose."));
		assertTrue( cdsCard.getDetail().contains("Parenteral"));
		assertTrue( cdsCard.getDetail().contains("Oral"));
		assertEquals("317249006", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals("782087002", cdsCard.getReferenceMedications().get(1).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=A02BA02"));
	}

	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForMultipleDrugsHavingSameSubstance_WithDifferentManufacturedDoseForms() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithDifferentManufacturedDosageFormAndDifferentRouteOfAdministration.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());

		CDSCard cdsCard = cards.get(0);
		assertEquals("The amount of Ranitidine prescribed is 64 times the average daily dose.", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertTrue( cdsCard.getDetail().contains("Conclusion : Combined prescribed amount is 64.00 times the average daily dose."));
		assertTrue( cdsCard.getDetail().contains("Ranitidine (as ranitidine hydrochloride) 150 mg oral tablet"));
		assertTrue( cdsCard.getDetail().contains("Ranitidine (as ranitidine hydrochloride) 25 mg/mL solution for injection"));
		assertEquals("317249006", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals("782087002", cdsCard.getReferenceMedications().get(1).getCoding().get(0).getCode());
		assertTrue(cdsCard.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=A02BA02"));
	}

	@Test
	public void shouldReturnOverDoseAlert_WhenPrescribedDailyDoseExceedsThresholdFactor_ForSingleDrugHavingMultipleSubstances() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundleWithCombinatorialDrug.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(2, cards.size());

		CDSCard cdsCard1 = cards.get(0);
		assertEquals("The amount of Probenecid prescribed is 6 times the average daily dose.", cdsCard1.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard1.getIndicator());
		assertTrue( cdsCard1.getDetail().contains("Conclusion : Combined prescribed amount is 6.00 times the average daily dose."));
		assertEquals("433216006", cdsCard1.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals(1, cdsCard1.getReferenceMedications().size());
		assertTrue(cdsCard1.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=M04AB01"));

		CDSCard cdsCard2 = cards.get(1);
		assertEquals("The amount of Colchicine prescribed is 6 times the average daily dose.", cdsCard2.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard2.getIndicator());
		assertTrue( cdsCard2.getDetail().contains("Conclusion : Combined prescribed amount is 6.00 times the average daily dose."));
		assertEquals("433216006", cdsCard2.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals(1, cdsCard2.getReferenceMedications().size());
		assertTrue(cdsCard2.getSource().getUrl().contains("https://www.whocc.no/atc_ddd_index/?code=M04AC01"));

	}
}
