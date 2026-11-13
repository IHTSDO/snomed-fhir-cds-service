package org.snomed.cdsservice.service.cql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.DataRequirement;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlanDefinitionHooksTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void hooksAreTheDistinctNamedEventTriggers() throws Exception {
		var planDefinition = objectMapper.readTree("""
				{ "resourceType": "PlanDefinition", "action": [
				  { "trigger": [ { "type": "named-event", "name": "encounter-start" }, { "type": "named-event", "name": "encounter-discharge" } ] },
				  { "trigger": [ { "type": "named-event", "name": "encounter-start" }, { "type": "periodic" } ] } ] }
				""");
		assertEquals(List.of("encounter-start", "encounter-discharge"), PlanDefinitionServiceRegistrar.hooks(planDefinition, "patient-view"));
	}

	@Test
	void withoutTriggersTheDefaultHookIsUsed() throws Exception {
		var planDefinition = objectMapper.readTree("""
				{ "resourceType": "PlanDefinition", "action": [ { "title": "No trigger" } ] }
				""");
		assertEquals(List.of("patient-view"), PlanDefinitionServiceRegistrar.hooks(planDefinition, "patient-view"));
	}

	@Test
	void encounterHooksPrefetchTheEncounterItself() {
		List<DataRequirement> requirements = List.of(new DataRequirement().setType("Encounter"), new DataRequirement().setType("Observation"));

		assertEquals(Map.of("patient", "Patient/{{context.patientId}}",
						"encounter", "Encounter/{{context.encounterId}}",
						"observation", "Observation?patient={{context.patientId}}"),
				PlanDefinitionCDSService.prefetchFor(requirements, "encounter-discharge"));
		assertEquals("Encounter?patient={{context.patientId}}",
				PlanDefinitionCDSService.prefetchFor(requirements, "patient-view").get("encounter"));
	}
}
