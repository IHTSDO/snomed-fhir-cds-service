package org.snomed.cdsservice.model.rules;

/**
 * Three-valued (Kleene) logic used when evaluating diagnostic criteria.
 * <p>
 * The distinction between {@link #FALSE} and {@link #UNKNOWN} is clinically important: a criterion is
 * only {@code FALSE} when the supplied data actively contradicts it, whereas {@code UNKNOWN} means the
 * data required to decide was missing or could not be interpreted safely. Absence of evidence must not
 * be treated as evidence of absence.
 */
public enum TruthValue {

	TRUE,
	FALSE,
	UNKNOWN;

	/**
	 * Three-valued AND. FALSE dominates; otherwise UNKNOWN dominates; otherwise TRUE.
	 */
	public TruthValue and(TruthValue other) {
		if (this == FALSE || other == FALSE) {
			return FALSE;
		}
		if (this == UNKNOWN || other == UNKNOWN) {
			return UNKNOWN;
		}
		return TRUE;
	}

	/**
	 * Three-valued OR. TRUE dominates; otherwise UNKNOWN dominates; otherwise FALSE.
	 */
	public TruthValue or(TruthValue other) {
		if (this == TRUE || other == TRUE) {
			return TRUE;
		}
		if (this == UNKNOWN || other == UNKNOWN) {
			return UNKNOWN;
		}
		return FALSE;
	}

	/**
	 * Three-valued NOT. UNKNOWN negates to UNKNOWN.
	 */
	public TruthValue negate() {
		return switch (this) {
			case TRUE -> FALSE;
			case FALSE -> TRUE;
			case UNKNOWN -> UNKNOWN;
		};
	}
}
