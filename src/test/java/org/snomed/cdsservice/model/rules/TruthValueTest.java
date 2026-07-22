package org.snomed.cdsservice.model.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.snomed.cdsservice.model.rules.TruthValue.FALSE;
import static org.snomed.cdsservice.model.rules.TruthValue.TRUE;
import static org.snomed.cdsservice.model.rules.TruthValue.UNKNOWN;

class TruthValueTest {

	@Test
	void andTruthTable() {
		assertEquals(TRUE, TRUE.and(TRUE));
		assertEquals(FALSE, TRUE.and(FALSE));
		assertEquals(UNKNOWN, TRUE.and(UNKNOWN));

		assertEquals(FALSE, FALSE.and(TRUE));
		assertEquals(FALSE, FALSE.and(FALSE));
		assertEquals(FALSE, FALSE.and(UNKNOWN));

		assertEquals(UNKNOWN, UNKNOWN.and(TRUE));
		assertEquals(FALSE, UNKNOWN.and(FALSE));
		assertEquals(UNKNOWN, UNKNOWN.and(UNKNOWN));
	}

	@Test
	void orTruthTable() {
		assertEquals(TRUE, TRUE.or(TRUE));
		assertEquals(TRUE, TRUE.or(FALSE));
		assertEquals(TRUE, TRUE.or(UNKNOWN));

		assertEquals(TRUE, FALSE.or(TRUE));
		assertEquals(FALSE, FALSE.or(FALSE));
		assertEquals(UNKNOWN, FALSE.or(UNKNOWN));

		assertEquals(TRUE, UNKNOWN.or(TRUE));
		assertEquals(UNKNOWN, UNKNOWN.or(FALSE));
		assertEquals(UNKNOWN, UNKNOWN.or(UNKNOWN));
	}

	@Test
	void notTruthTable() {
		assertEquals(FALSE, TRUE.negate());
		assertEquals(TRUE, FALSE.negate());
		assertEquals(UNKNOWN, UNKNOWN.negate());
	}
}
