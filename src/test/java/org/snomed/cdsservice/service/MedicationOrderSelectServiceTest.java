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

@SpringBootTest
class MedicationOrderSelectServiceTest {

	@MockBean
	private MedicationConditionRuleLoaderService ruleLoaderService;

	@MockBean
	private MedicationRuleLoaderService medicationRuleLoaderService;

	@Autowired
	private MedicationOrderSelectService service;

	@BeforeEach
	void setMockOutput() {
		CDSTrigger trigger = new MedicationConditionCDSTrigger(
				"Atorvastatin",
				Collections.singleton(new Coding("http://snomed.info/sct", "108600003", null)),
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
						Stream.of(new CDSReference(Collections.singletonList(new CDSCoding("http://snomed.info/sct", "108600003")))).collect(Collectors.toList()),
						new CDSReference(Collections.singletonList(new CDSCoding("http://snomed.info/sct", "197321007")))));
		service.setMedicationOrderSelectTriggers(List.of(trigger));
	}

	@Test
	public void test() throws IOException {
		CDSRequest cdsRequest = new CDSRequest();
		cdsRequest.setPrefetchStrings(Map.of(
				"patient", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/PatientResource.json"), StandardCharsets.UTF_8),
				"conditions", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/ConditionBundle.json"), StandardCharsets.UTF_8),
				"draftMedicationRequests", StreamUtils.copyToString(getClass().getResourceAsStream("/medication-order-select/MedicationRequestBundle.json"), StandardCharsets.UTF_8)
		));

		List<CDSCard> cards = service.call(cdsRequest);
		assertEquals(1, cards.size());

		CDSCard cdsCard = cards.get(0);
		assertEquals("Contraindication: \"Atorvastatin-containing product\" with patient condition \"Steatosis of liver\".", cdsCard.getSummary());
		assertEquals(CDSIndicator.warning, cdsCard.getIndicator());
		assertEquals("The use of Atorvastatin is contraindicated when the patient has Disease of liver.", cdsCard.getDetail());
		assertEquals("108600003", cdsCard.getReferenceMedications().get(0).getCoding().get(0).getCode());
		assertEquals("197321007", cdsCard.getReferenceCondition().getCoding().get(0).getCode());
	}

}
