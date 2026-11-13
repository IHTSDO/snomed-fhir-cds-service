package org.snomed.cdsservice.service.cql;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarePlanCardMapperTest {

	private final CarePlanCardMapper mapper = new CarePlanCardMapper(CarePlanCardMapper.parseIgPageBases(List.of(
			"http://smart.who.int/immunizations=https://smart.who.int/immunizations",
			"http://smart.who.int/anc=https://build.fhir.org/ig/WorldHealthOrganization/smart-anc/")));

	@Test
	void canonicalsOfPublishedIgsLinkToTheirPages() {
		assertEquals(Optional.of("https://smart.who.int/immunizations/PlanDefinition-IMMZD2DTBCG.html"),
				mapper.igPage("http://smart.who.int/immunizations/PlanDefinition/IMMZD2DTBCG"));
		assertEquals(Optional.of("https://smart.who.int/immunizations/Library-IMMZD2DTBCGLogic.html"),
				mapper.igPage("http://smart.who.int/immunizations/Library/IMMZD2DTBCGLogic|1.0.0"));
		assertEquals(Optional.of("https://build.fhir.org/ig/WorldHealthOrganization/smart-anc/PlanDefinition-ANCDT01.html"),
				mapper.igPage("http://smart.who.int/anc/PlanDefinition/ANCDT01"));
	}

	@Test
	void canonicalsWithoutAPublishedIgGetNoPage() {
		assertEquals(Optional.empty(), mapper.igPage("http://snomed.org/fhir/cds-demo/PlanDefinition/DM2024OGTT01"));
		assertEquals(Optional.empty(), mapper.igPage(null));
	}
}
