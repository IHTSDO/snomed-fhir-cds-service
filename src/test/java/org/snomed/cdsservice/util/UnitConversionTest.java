package org.snomed.cdsservice.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnitConversionTest {

	@Test
	void shouldResolveTabletConversion_WhenUnitsUseDifferentCaseOrDisplayVariants() {
		assertEquals(1.0, UnitConversion.factorOfConversion("tablet", "Tablet"));
		assertEquals(1.0, UnitConversion.factorOfConversion("Tablet", "Tablet (unit of presentation)"));
		assertEquals(1.0, UnitConversion.factorOfConversion("tablet", "Tablet (unit of presentation)"));
	}
}
