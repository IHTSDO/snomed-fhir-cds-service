package org.snomed.cdsservice.service.rules;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EclSelectorTest {

	private static final String SNOMED = EclSelector.SNOMED_SYSTEM;

	@Test
	void bareConceptResolvesLocally() {
		EclSelector.Resolution resolution = EclSelector.parse("271062006");
		assertTrue(resolution.resolvableLocally());
		assertEquals(Set.of(new CodeKey(SNOMED, "271062006")), resolution.codes());
	}

	@Test
	void conceptWithTermResolvesLocally() {
		EclSelector.Resolution resolution = EclSelector.parse("271062006 |Fasting blood glucose measurement|");
		assertTrue(resolution.resolvableLocally());
		assertEquals(Set.of(new CodeKey(SNOMED, "271062006")), resolution.codes());
	}

	@Test
	void disjunctionOfConceptsResolvesLocally() {
		EclSelector.Resolution resolution = EclSelector.parse(
				"28442001 |Polyuria| OR 17173007 |Excessive thirst| OR 267023007 |Excessive eating|");
		assertTrue(resolution.resolvableLocally());
		assertEquals(Set.of(
				new CodeKey(SNOMED, "28442001"),
				new CodeKey(SNOMED, "17173007"),
				new CodeKey(SNOMED, "267023007")), resolution.codes());
	}

	@Test
	void orInsideTermDoesNotSplit() {
		// " OR " appearing inside a |term| must not be treated as a disjunction separator.
		EclSelector.Resolution resolution = EclSelector.parse("420422005 |Ketoacidosis OR ketosis due to diabetes|");
		assertTrue(resolution.resolvableLocally());
		assertEquals(Set.of(new CodeKey(SNOMED, "420422005")), resolution.codes());
	}

	@Test
	void descendantOperatorRequiresServer() {
		assertFalse(EclSelector.parse("< 441656006").resolvableLocally());
		assertFalse(EclSelector.parse("<< 420422005 |Ketoacidosis due to diabetes mellitus|").resolvableLocally());
	}

	@Test
	void descendantWithinDisjunctionRequiresServer() {
		assertFalse(EclSelector.parse("<< 420422005 OR << 441656006").resolvableLocally());
	}

	@Test
	void refinementRequiresServer() {
		assertFalse(EclSelector.parse("271062006 : 363714003 = 386053000").resolvableLocally());
	}

	@Test
	void wildcardRequiresServer() {
		assertFalse(EclSelector.parse("*").resolvableLocally());
	}

	@Test
	void requiresServerResolutionCarriesOriginalEcl() {
		EclSelector.Resolution resolution = EclSelector.parse("< 441656006");
		assertFalse(resolution.resolvableLocally());
		assertTrue(resolution.codes().isEmpty());
		assertEquals("< 441656006", resolution.ecl());
	}

	@Test
	void blankSelectorIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> EclSelector.parse("  "));
		assertThrows(IllegalArgumentException.class, () -> EclSelector.parse(null));
	}
}
