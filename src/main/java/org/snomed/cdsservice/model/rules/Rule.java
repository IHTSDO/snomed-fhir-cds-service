package org.snomed.cdsservice.model.rules;

import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.service.rules.expression.RuleExpression;

/**
 * An immutable diagnostic rule loaded from a rules TSV file. It combines atomic criteria through a parsed
 * Boolean {@link #expression()} and carries the clinical decision and card-presentation information.
 * <p>
 * The raw {@link #logicExpression()} string is preserved for traceability, but evaluation always uses the
 * parsed {@link #expression()} AST so the model can later target CQL without redesign.
 */
public record Rule(
		String ruleId,
		String cardUuid,
		String version,
		boolean enabled,
		String serviceId,
		String hook,
		String pathway,
		String triggerEvent,
		String logicExpression,
		RuleExpression expression,
		OutcomeStatus outcomeStatus,
		ActionType actionType,
		String outcomeCodeSystem,
		String outcomeCode,
		String outcomeDisplay,
		boolean suppressIfOutcomePresent,
		CDSIndicator cardIndicator,
		String cardSummary,
		String cardDetail,
		String sourceLabel,
		String sourceUrl,
		String implementationNotes
) {
}
