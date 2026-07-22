package org.snomed.cdsservice.model.rules;

import java.math.BigDecimal;
import java.util.Set;

/**
 * An immutable atomic clinical criterion loaded from a criteria TSV file. A criterion defines how to
 * locate and evaluate one clinical fact in the supplied FHIR resources or CDS Hooks context.
 * <p>
 * The record intentionally mirrors the TSV columns so the loader can map fields directly and the
 * evaluators can read exactly what the content author expressed. Structural validation and consistency
 * checks are performed by the loader (which can report the offending file and row), not here.
 */
public record Criterion(
		String criterionId,
		String label,
		CriterionType criterionType,
		String resourceType,
		String codeSystem,
		CodeSelectorType codeSelectorType,
		String codeSelector,
		CodeMatchType codeMatch,
		String componentCodeSystem,
		String componentCode,
		CriterionOperator operator,
		BigDecimal valueLow,
		BigDecimal valueHigh,
		Boolean lowerInclusive,
		Boolean upperInclusive,
		String unitSystem,
		String unitCode,
		String unitDisplay,
		String unitSnomedCode,
		Set<String> acceptedStatuses,
		String timeAspectSystem,
		String timeAspectCode,
		String aggregationSystem,
		String aggregationCode,
		int minOccurrences,
		DistinctBy distinctBy,
		String contextSource,
		String contextKey,
		CriterionOperator contextOperator,
		String contextValue,
		MissingDataBehavior missingDataBehavior,
		String notes
) {

	/**
	 * @return true when a two-hour aspect, 24-hour aspect, aggregation or similar semantic qualifier must
	 * be verified on the matching Observation.
	 */
	public boolean hasSemanticQualifier() {
		return isPresent(timeAspectCode) || isPresent(aggregationCode);
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}
