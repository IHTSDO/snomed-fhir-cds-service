package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConceptMapBindingsTest {

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();
	private static final String LOCAL = "http://example.org/CodeSystem/local";
	private static final String SCT = "http://snomed.info/sct";

	@Test
	void bindsEnumeratedValueSetsIncludingNestedOnes() {
		ValueSet leaf = valueSet("http://example.org/ValueSet/leaf", "A");
		ValueSet grouper = new ValueSet();
		grouper.setUrl("http://example.org/ValueSet/grouper");
		grouper.getCompose().addInclude().addValueSet("http://example.org/ValueSet/leaf|1.0.0");
		grouper.getCompose().addInclude().setSystem(LOCAL).addConcept().setCode("B");
		ValueSet unmapped = valueSet("http://example.org/ValueSet/unmapped", "C");
		ValueSet filtered = valueSet("http://example.org/ValueSet/filtered", "A");
		filtered.getCompose().getIncludeFirstRep().addFilter().setProperty("concept").setOp(ValueSet.FilterOperator.ISA).setValue("A");

		PlanDefinitionEngine engine = new PlanDefinitionEngine(FHIR_CONTEXT, List.<IBaseResource>of(leaf, grouper, unmapped, filtered));
		Set<String> bound = new ConceptMapBindings(List.of(conceptMap())).applyTo(engine);

		assertEquals(Set.of("http://example.org/ValueSet/leaf", "http://example.org/ValueSet/grouper"), bound);
		assertEquals(Set.of(SCT + "/900000000000207008/version/20260701"), engine.getTerminologyVersions());
	}

	private static ValueSet valueSet(String url, String code) {
		ValueSet valueSet = new ValueSet();
		valueSet.setUrl(url);
		valueSet.setVersion("1.0.0");
		valueSet.getCompose().addInclude().setSystem(LOCAL).addConcept().setCode(code);
		return valueSet;
	}

	/** A → 1 (equivalent), B → unmatched, C → 3 (disjoint). */
	private static ConceptMap conceptMap() {
		ConceptMap conceptMap = new ConceptMap();
		ConceptMap.ConceptMapGroupComponent group = conceptMap.addGroup().setSource(LOCAL).setTarget(SCT)
				.setTargetVersion(SCT + "/900000000000207008/version/20260701");
		group.addElement().setCode("A").addTarget().setCode("1").setEquivalence(ConceptMapEquivalence.EQUIVALENT);
		group.addElement().setCode("B").addTarget().setEquivalence(ConceptMapEquivalence.UNMATCHED);
		group.addElement().setCode("C").addTarget().setCode("3").setEquivalence(ConceptMapEquivalence.DISJOINT);
		return conceptMap;
	}
}
