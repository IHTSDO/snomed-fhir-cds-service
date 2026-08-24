package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.service.rules.CodeKey;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Default semantic-qualifier convention for the demonstrator.
 * <p>
 * A criterion's {@code time_aspect} and {@code aggregation} concepts are considered to apply to an
 * Observation when their {@code system|code} appears among the Observation's own codings, collected from:
 * <ul>
 *     <li>{@code Observation.code.coding}</li>
 *     <li>{@code Observation.category[].coding}</li>
 *     <li>{@code Observation.method.coding}</li>
 *     <li>{@code Observation.component[].code.coding}</li>
 * </ul>
 * A national FHIR profile that wants these qualifiers honoured must place the corresponding SNOMED CT
 * concept in one of those elements. When a declared qualifier is not found, the match is
 * {@link QualifierMatchResult#UNDETERMINED} — never silently matched.
 */
public class CodingBasedSemanticQualifierMatcher implements ObservationSemanticQualifierMatcher {

	@Override
	public QualifierMatchResult matches(Observation observation, Criterion criterion) {
		Set<CodeKey> required = new HashSet<>();
		if (isPresent(criterion.timeAspectCode())) {
			required.add(new CodeKey(criterion.timeAspectSystem(), criterion.timeAspectCode()));
		}
		if (isPresent(criterion.aggregationCode())) {
			required.add(new CodeKey(criterion.aggregationSystem(), criterion.aggregationCode()));
		}
		if (required.isEmpty()) {
			return QualifierMatchResult.NOT_APPLICABLE;
		}

		Set<CodeKey> observationCodes = collectObservationCodings(observation);
		return observationCodes.containsAll(required) ? QualifierMatchResult.MATCHED : QualifierMatchResult.UNDETERMINED;
	}

	private Set<CodeKey> collectObservationCodings(Observation observation) {
		Set<CodeKey> codes = new HashSet<>();
		addCodings(codes, observation.hasCode() ? observation.getCode() : null);
		if (observation.hasCategory()) {
			for (CodeableConcept category : observation.getCategory()) {
				addCodings(codes, category);
			}
		}
		if (observation.hasMethod()) {
			addCodings(codes, observation.getMethod());
		}
		if (observation.hasComponent()) {
			for (Observation.ObservationComponentComponent component : observation.getComponent()) {
				addCodings(codes, component.hasCode() ? component.getCode() : null);
			}
		}
		return codes;
	}

	private void addCodings(Set<CodeKey> target, CodeableConcept concept) {
		if (concept == null) {
			return;
		}
		List<Coding> codings = concept.getCoding();
		for (Coding coding : codings) {
			target.add(CodeKey.of(coding));
		}
	}

	private static boolean isPresent(String value) {
		return value != null && !value.isBlank();
	}
}
