package org.snomed.cdsservice.service.cql;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CarePlanCardMapperTest {

	private final CarePlanCardMapper mapper = new CarePlanCardMapper(Set.of("smart.who.int"));

	@Test
	void canonicalsOfPublishedIgsLinkToTheirPages() {
		assertEquals(Optional.of("https://smart.who.int/immunizations/PlanDefinition-IMMZD2DTBCG.html"),
				mapper.igPage("http://smart.who.int/immunizations/PlanDefinition/IMMZD2DTBCG"));
		assertEquals(Optional.of("https://smart.who.int/immunizations/Library-IMMZD2DTBCGLogic.html"),
				mapper.igPage("http://smart.who.int/immunizations/Library/IMMZD2DTBCGLogic|1.0.0"));
	}

	@Test
	void canonicalsWithoutAPublishedIgGetNoPage() {
		assertEquals(Optional.empty(), mapper.igPage("http://snomed.org/fhir/cds-demo/PlanDefinition/DM2024OGTT01"));
		assertEquals(Optional.empty(), mapper.igPage(null));
	}
}
