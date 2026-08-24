package org.snomed.cdsservice.service.rules.evaluator;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.snomed.cdsservice.model.rules.CodeMatchType;
import org.snomed.cdsservice.model.rules.CodeSelectorType;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionOperator;
import org.snomed.cdsservice.model.rules.CriterionType;
import org.snomed.cdsservice.model.rules.DistinctBy;
import org.snomed.cdsservice.model.rules.MissingDataBehavior;
import org.snomed.cdsservice.service.rules.CodeKey;
import org.snomed.cdsservice.service.rules.CodeResolver;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Shared fixtures for the criterion evaluator tests: concise builders for criteria and Observations and a
 * simple exact-code {@link CodeResolver}.
 */
public final class EvaluatorTestSupport {

	public static final String SNOMED = "http://snomed.info/sct";
	public static final String UCUM = "http://unitsofmeasure.org";

	private EvaluatorTestSupport() {
	}

	/** Resolves a criterion's main code_selector to its exact {@code system|code} (no descendants). */
	public static CodeResolver exactCodeResolver() {
		return criterion -> Set.of(new CodeKey(criterion.codeSystem(), criterion.codeSelector()));
	}

	public static ObservationCriterionSupport observationSupport() {
		return new ObservationCriterionSupport(exactCodeResolver(), new CodingBasedSemanticQualifierMatcher());
	}

	public static Criterion observationValue(String code, CriterionOperator operator, String valueLow, String unitCode,
									  int minOccurrences, DistinctBy distinctBy) {
		return new Criterion("C_" + code, "Test observation " + code, CriterionType.OBSERVATION_VALUE, "Observation",
				SNOMED, null, code, null, null, null, operator,
				valueLow == null ? null : new BigDecimal(valueLow), null, true, null,
				UCUM, unitCode, unitCode, null, Set.of("final", "amended", "corrected"),
				null, null, null, null, minOccurrences, distinctBy,
				null, null, null, null, MissingDataBehavior.UNKNOWN, "test");
	}

	public static Criterion observationComponentValue(String parentCode, String componentCode, CriterionOperator operator,
											   String valueLow, String valueHigh) {
		return observationComponentValue(parentCode, componentCode, operator, valueLow, valueHigh, 1, DistinctBy.NONE);
	}

	public static Criterion observationComponentValue(String parentCode, String componentCode, CriterionOperator operator,
											   String valueLow, String valueHigh, int minOccurrences, DistinctBy distinctBy) {
		Boolean upperInclusive = valueHigh == null ? null : false;
		return new Criterion("C_" + parentCode + "_" + componentCode, "Test component", CriterionType.OBSERVATION_COMPONENT_VALUE,
				"Observation", SNOMED, null, parentCode, null, SNOMED, componentCode, operator,
				valueLow == null ? null : new BigDecimal(valueLow), valueHigh == null ? null : new BigDecimal(valueHigh),
				true, upperInclusive, UCUM, "mm[Hg]", "mmHg", null, Set.of("final"),
				null, null, null, null, minOccurrences, distinctBy,
				null, null, null, null, MissingDataBehavior.UNKNOWN, "test");
	}

	public static Criterion observationValueWithTimeAspect(String code, String timeAspectCode) {
		return new Criterion("C_" + code, "Test observation " + code, CriterionType.OBSERVATION_VALUE, "Observation",
				SNOMED, null, code, null, null, null, CriterionOperator.GE,
				new BigDecimal("11.1"), null, true, null,
				UCUM, "mmol/L", "mmol/L", null, Set.of("final"),
				SNOMED, timeAspectCode, null, null, 1, DistinctBy.NONE,
				null, null, null, null, MissingDataBehavior.UNKNOWN, "test");
	}

	public static Criterion namedCriterion(String criterionId) {
		return new Criterion(criterionId, "Criterion " + criterionId, CriterionType.OBSERVATION_VALUE, "Observation",
				SNOMED, null, "0000", null, null, null, CriterionOperator.GE, new BigDecimal("1"), null, true, null,
				UCUM, "mmol/L", "mmol/L", null, Set.of("final"), null, null, null, null, 1, DistinctBy.NONE,
				null, null, null, null, MissingDataBehavior.UNKNOWN, "test");
	}

	public static Criterion codedResourcePresent(String resourceType, CriterionOperator operator) {
		return new Criterion("C_coded_" + resourceType, "Coded resource present", CriterionType.CODED_RESOURCE_PRESENT,
				resourceType, null, CodeSelectorType.VALUESET, "http://example.org/fhir/ValueSet/test", CodeMatchType.VALUESET,
				null, null, operator, null, null, null, null, null, null, null, null, Set.of(),
				null, null, null, null, 1, DistinctBy.NONE, null, null, null, null, MissingDataBehavior.UNKNOWN, "test");
	}

	public static Criterion contextValue(String contextKey, String expectedValue) {
		return new Criterion("C_context", "Context value", CriterionType.CONTEXT_VALUE, null, null, null, null, null,
				null, null, CriterionOperator.EQ, null, null, null, null, null, null, null, null, Set.of(),
				null, null, null, null, 1, DistinctBy.NONE, "CDS_HOOK_CONTEXT", contextKey, CriterionOperator.EQ,
				expectedValue, MissingDataBehavior.UNKNOWN, "test");
	}

	public static CodeResolver resolverReturning(CodeKey... keys) {
		return criterion -> Set.of(keys);
	}

	public static Condition condition(String code, String clinicalStatusCode, String verificationStatusCode) {
		Condition condition = new Condition();
		condition.getCode().addCoding().setSystem(SNOMED).setCode(code);
		if (clinicalStatusCode != null) {
			condition.setClinicalStatus(statusConcept("http://terminology.hl7.org/CodeSystem/condition-clinical", clinicalStatusCode));
		}
		if (verificationStatusCode != null) {
			condition.setVerificationStatus(statusConcept("http://terminology.hl7.org/CodeSystem/condition-ver-status", verificationStatusCode));
		}
		return condition;
	}

	private static CodeableConcept statusConcept(String system, String code) {
		CodeableConcept concept = new CodeableConcept();
		concept.addCoding().setSystem(system).setCode(code);
		return concept;
	}

	public static Observation observation(String code, Observation.ObservationStatus status, String value, String unitSystem,
								   String unitCode, String effectiveDate) {
		Observation observation = new Observation();
		observation.setStatus(status);
		observation.getCode().addCoding().setSystem(SNOMED).setCode(code);
		Quantity quantity = new Quantity();
		if (value != null) {
			quantity.setValue(new BigDecimal(value));
		}
		quantity.setSystem(unitSystem);
		quantity.setCode(unitCode);
		observation.setValue(quantity);
		if (effectiveDate != null) {
			observation.setEffective(new DateTimeType(effectiveDate));
		}
		return observation;
	}

	public static Observation componentObservation(String parentCode, String componentCode, String value, String effectiveDate) {
		Observation observation = new Observation();
		observation.setStatus(Observation.ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem(SNOMED).setCode(parentCode);
		Observation.ObservationComponentComponent component = observation.addComponent();
		component.getCode().addCoding().setSystem(SNOMED).setCode(componentCode);
		Quantity quantity = new Quantity().setValue(new BigDecimal(value)).setSystem(UCUM).setCode("mm[Hg]");
		component.setValue(quantity);
		if (effectiveDate != null) {
			observation.setEffective(new DateTimeType(effectiveDate));
		}
		return observation;
	}

	public static Observation bpObservation(String parentCode, String effectiveDate) {
		Observation observation = new Observation();
		observation.setStatus(Observation.ObservationStatus.FINAL);
		observation.getCode().addCoding().setSystem(SNOMED).setCode(parentCode);
		if (effectiveDate != null) {
			observation.setEffective(new DateTimeType(effectiveDate));
		}
		return observation;
	}

	public static Observation addComponent(Observation observation, String componentCode, String value) {
		Observation.ObservationComponentComponent component = observation.addComponent();
		component.getCode().addCoding().setSystem(SNOMED).setCode(componentCode);
		component.setValue(new Quantity().setValue(new BigDecimal(value)).setSystem(UCUM).setCode("mm[Hg]"));
		return observation;
	}

	public static Observation withEncounter(Observation observation, String encounterReference) {
		observation.setEncounter(new Reference(encounterReference));
		return observation;
	}

	public static Observation withCoding(Observation observation, String system, String code) {
		observation.getCode().addCoding().setSystem(system).setCode(code);
		return observation;
	}
}
