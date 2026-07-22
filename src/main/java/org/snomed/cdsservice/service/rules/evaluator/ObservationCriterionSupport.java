package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Resource;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionEvaluationResult;
import org.snomed.cdsservice.model.rules.TruthValue;
import org.snomed.cdsservice.service.rules.CodeKey;
import org.snomed.cdsservice.service.rules.CodeResolver;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared evaluation pipeline for numeric Observation criteria. It is used by both the
 * {@code OBSERVATION_VALUE} and {@code OBSERVATION_COMPONENT_VALUE} evaluators through composition: they
 * supply a {@link QuantityExtractor} that decides which {@link Quantity} on an Observation carries the
 * value to test. Everything else — code matching, status filtering, semantic qualifiers, UCUM unit
 * checking, {@link BigDecimal} comparison, occurrence counting and distinctness — is common.
 * <p>
 * Missing data never becomes a FALSE: a result is UNKNOWN whenever the answer could change if the missing
 * information were supplied (no matching observation, an uninterpretable unit or qualifier, or an
 * undeterminable distinctness key).
 */
public class ObservationCriterionSupport {

	/** Selects the Quantity to test on a matching Observation, or null if this observation carries none. */
	@FunctionalInterface
	public interface QuantityExtractor {
		Quantity extract(Observation observation, Criterion criterion);
	}

	private static final DateTimeFormatter CALENDAR_DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

	private final CodeResolver codeResolver;
	private final ObservationSemanticQualifierMatcher qualifierMatcher;

	public ObservationCriterionSupport(CodeResolver codeResolver, ObservationSemanticQualifierMatcher qualifierMatcher) {
		this.codeResolver = codeResolver;
		this.qualifierMatcher = qualifierMatcher;
	}

	public CriterionEvaluationResult evaluate(Criterion criterion, RuleEvaluationContext context, QuantityExtractor extractor) {
		Set<CodeKey> acceptableCodes = codeResolver.acceptableCodes(criterion);

		List<Observation> qualifying = new ArrayList<>();
		boolean anyCodeMatch = false;
		boolean anyFailingValue = false;
		boolean anyIndeterminate = false;

		for (Observation observation : context.getResources(Observation.class)) {
			if (!codeMatches(observation, acceptableCodes)) {
				continue;
			}
			anyCodeMatch = true;

			if (!statusAccepted(observation, criterion)) {
				continue;
			}

			QualifierMatchResult qualifier = qualifierMatcher.matches(observation, criterion);
			if (qualifier == QualifierMatchResult.UNDETERMINED) {
				anyIndeterminate = true;
				continue;
			}

			Quantity quantity = extractor.extract(observation, criterion);
			if (quantity == null) {
				// This observation does not carry the required measurement (e.g. component absent).
				continue;
			}

			UnitComparability unit = unitComparability(criterion, quantity);
			if (unit == UnitComparability.INCOMPARABLE) {
				anyIndeterminate = true;
				continue;
			}

			if (!quantity.hasValue()) {
				anyIndeterminate = true;
				continue;
			}

			if (operatorPasses(criterion, quantity.getValue())) {
				qualifying.add(observation);
			} else {
				anyFailingValue = true;
			}
		}

		DistinctnessResult distinctness = countDistinct(qualifying, criterion);
		int minOccurrences = criterion.minOccurrences();

		if (distinctness.determinedCount() >= minOccurrences) {
			return CriterionEvaluationResult.of(criterion, TruthValue.TRUE, toResources(qualifying),
					"%d qualifying observation(s) met the threshold.".formatted(distinctness.determinedCount()));
		}

		boolean distinctnessCouldReachMin = distinctness.undeterminableCount() > 0
				&& distinctness.determinedCount() + distinctness.undeterminableCount() >= minOccurrences;
		if (anyIndeterminate || distinctnessCouldReachMin) {
			return CriterionEvaluationResult.of(criterion, TruthValue.UNKNOWN, toResources(qualifying),
					"Required data was present but could not be interpreted (unit, qualifier or distinctness undetermined).");
		}

		if (!anyCodeMatch) {
			return CriterionEvaluationResult.of(criterion, TruthValue.UNKNOWN, List.of(),
					"No observation matching the required code was supplied.");
		}

		if (anyFailingValue || !qualifying.isEmpty()) {
			return CriterionEvaluationResult.of(criterion, TruthValue.FALSE, List.of(),
					"Matching observations were supplied but did not meet the criterion.");
		}

		return CriterionEvaluationResult.of(criterion, TruthValue.UNKNOWN, List.of(),
				"No qualifying observation could be evaluated.");
	}

	private boolean codeMatches(Observation observation, Set<CodeKey> acceptableCodes) {
		if (!observation.hasCode()) {
			return false;
		}
		return observation.getCode().getCoding().stream().anyMatch(coding -> acceptableCodes.contains(CodeKey.of(coding)));
	}

	private boolean statusAccepted(Observation observation, Criterion criterion) {
		Set<String> accepted = criterion.acceptedStatuses();
		if (accepted == null || accepted.isEmpty()) {
			return true;
		}
		if (!observation.hasStatus()) {
			return false;
		}
		return accepted.contains(observation.getStatus().toCode().toLowerCase());
	}

	private enum UnitComparability { COMPARABLE, INCOMPARABLE }

	private UnitComparability unitComparability(Criterion criterion, Quantity quantity) {
		String requiredCode = criterion.unitCode();
		if (requiredCode == null || requiredCode.isBlank()) {
			return UnitComparability.COMPARABLE;
		}
		String requiredSystem = criterion.unitSystem();
		if (requiredSystem != null && !requiredSystem.isBlank()) {
			if (!requiredSystem.equals(quantity.getSystem())) {
				return UnitComparability.INCOMPARABLE;
			}
		}
		// No unit conversion is attempted: the incoming UCUM code must equal the configured code.
		if (!requiredCode.equals(quantity.getCode())) {
			return UnitComparability.INCOMPARABLE;
		}
		return UnitComparability.COMPARABLE;
	}

	private boolean operatorPasses(Criterion criterion, BigDecimal actual) {
		return switch (criterion.operator()) {
			case GE -> actual.compareTo(criterion.valueLow()) >= 0;
			case GT -> actual.compareTo(criterion.valueLow()) > 0;
			case LE -> actual.compareTo(criterion.valueLow()) <= 0;
			case LT -> actual.compareTo(criterion.valueLow()) < 0;
			case EQ -> actual.compareTo(criterion.valueLow()) == 0;
			case BETWEEN -> passesLowerBound(criterion, actual) && passesUpperBound(criterion, actual);
			default -> false;
		};
	}

	private boolean passesLowerBound(Criterion criterion, BigDecimal actual) {
		int comparison = actual.compareTo(criterion.valueLow());
		return Boolean.TRUE.equals(criterion.lowerInclusive()) ? comparison >= 0 : comparison > 0;
	}

	private boolean passesUpperBound(Criterion criterion, BigDecimal actual) {
		int comparison = actual.compareTo(criterion.valueHigh());
		return Boolean.TRUE.equals(criterion.upperInclusive()) ? comparison <= 0 : comparison < 0;
	}

	private record DistinctnessResult(int determinedCount, int undeterminableCount) {
	}

	private DistinctnessResult countDistinct(List<Observation> qualifying, Criterion criterion) {
		switch (criterion.distinctBy()) {
			case NONE -> {
				return new DistinctnessResult(qualifying.size(), 0);
			}
			case CALENDAR_DAY -> {
				Set<String> days = new HashSet<>();
				int undeterminable = 0;
				for (Observation observation : qualifying) {
					String day = calendarDay(observation);
					if (day == null) {
						undeterminable++;
					} else {
						days.add(day);
					}
				}
				return new DistinctnessResult(days.size(), undeterminable);
			}
			case ENCOUNTER -> {
				Set<String> encounters = new HashSet<>();
				int undeterminable = 0;
				for (Observation observation : qualifying) {
					String encounter = observation.hasEncounter() ? observation.getEncounter().getReference() : null;
					if (encounter == null || encounter.isBlank()) {
						undeterminable++;
					} else {
						encounters.add(encounter);
					}
				}
				return new DistinctnessResult(encounters.size(), undeterminable);
			}
			default -> {
				return new DistinctnessResult(qualifying.size(), 0);
			}
		}
	}

	private String calendarDay(Observation observation) {
		Date date = null;
		if (observation.hasEffectiveDateTimeType() && observation.getEffectiveDateTimeType().getValue() != null) {
			date = observation.getEffectiveDateTimeType().getValue();
		} else if (observation.hasEffectivePeriod() && observation.getEffectivePeriod().hasStart()) {
			date = observation.getEffectivePeriod().getStart();
		} else if (observation.hasIssued()) {
			date = observation.getIssued();
		}
		return date == null ? null : CALENDAR_DAY.format(date.toInstant());
	}

	private List<Resource> toResources(List<Observation> observations) {
		return new ArrayList<>(observations);
	}
}
