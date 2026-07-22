package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The data available to criterion evaluators for one CDS Hooks request: the patient, the FHIR resources
 * supplied through prefetch (of any type), and the CDS Hooks {@code context} values.
 * <p>
 * Resources are held as a generic list rather than fixed typed fields so the engine can serve use cases
 * that need Procedure, Immunization, MedicationRequest, AllergyIntolerance and so on without changing the
 * context shape.
 */
public record RuleEvaluationContext(
		Patient patient,
		List<Resource> resources,
		Map<String, Object> hookContext
) {

	public RuleEvaluationContext {
		resources = resources == null ? List.of() : List.copyOf(resources);
		hookContext = hookContext == null ? Map.of() : Map.copyOf(hookContext);
	}

	/**
	 * @return every supplied resource assignable to the given FHIR type, in supplied order.
	 */
	public <T extends Resource> List<T> getResources(Class<T> type) {
		List<T> matches = new ArrayList<>();
		for (Resource resource : resources) {
			if (type.isInstance(resource)) {
				matches.add(type.cast(resource));
			}
		}
		return matches;
	}
}
