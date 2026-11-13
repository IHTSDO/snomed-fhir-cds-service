package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.PlanDefinition;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanDefinitionEngineWarmUpTest {

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();

	@Test
	void warmUpCompilesTheWhoContent() throws Exception {
		PlanDefinitionEngine engine = PlanDefinitionEngine.fromDirectory(FHIR_CONTEXT, Path.of("knowledge-bases/smart/immunizations-jm"));
		assertDoesNotThrow(() -> engine.warmUp("IMMZD2DTBCG"));
	}

	@Test
	void dataRequirementsDoNotBreakApplyOfActionsWithoutDefinition() throws Exception {
		// The WHO SMART ANC decision tables have no action definitions; $data-requirements used to leave empty ones.
		PlanDefinitionEngine engine = PlanDefinitionEngine.fromDirectory(FHIR_CONTEXT, Path.of("knowledge-bases/smart/anc-who"));
		engine.dataRequirements("ANCDT01");
		assertDoesNotThrow(() -> engine.warmUp("ANCDT01"));
	}

	@Test
	void invalidCqlFailsTheWarmUp() {
		Library library = new Library();
		library.setId("Broken");
		library.setUrl("http://example.org/Library/Broken");
		library.setName("Broken");
		library.setVersion("0.1.0");
		library.addContent().setContentType("text/cql").setData("""
				library Broken version '0.1.0'
				using FHIR version '4.0.1'
				context Patient
				define "Applies": ({ 1, 2 }) L where L >
				""".getBytes(StandardCharsets.UTF_8));
		PlanDefinition planDefinition = new PlanDefinition();
		planDefinition.setId("BrokenRule");
		planDefinition.setUrl("http://example.org/PlanDefinition/BrokenRule");
		planDefinition.addLibrary("http://example.org/Library/Broken");
		planDefinition.addAction().addCondition().setKind(PlanDefinition.ActionConditionKind.APPLICABILITY)
				.getExpression().setLanguage("text/cql-identifier").setExpression("Applies");

		PlanDefinitionEngine engine = new PlanDefinitionEngine(FHIR_CONTEXT, List.<IBaseResource>of(library, planDefinition));

		RuntimeException e = assertThrows(RuntimeException.class, () -> engine.warmUp("BrokenRule"));
		assertTrue(e.getMessage().contains("Syntax error"), e.getMessage());
	}
}
