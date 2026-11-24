package org.snomed.cdsservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledKnowledgeBaseTest {

	@Test
	void usesTheBundledKnowledgeBaseWhenNoDirectoryIsConfigured() throws Exception {
		StandardEnvironment environment = new StandardEnvironment();

		new BundledKnowledgeBase().postProcessEnvironment(environment, new SpringApplication());

		Path directory = Path.of(environment.getProperty(BundledKnowledgeBase.PROPERTY));
		// The rule tables, the diagnostic rules and the FHIR content, with their subdirectories.
		assertTrue(Files.isRegularFile(directory.resolve("CDS_Medication-Condition_Cards.tsv")));
		assertTrue(Files.isRegularFile(directory.resolve("diabetes_rules.tsv")));
		assertTrue(Files.isRegularFile(directory.resolve("smart/anc-who/PlanDefinition-ANCContactStart.json")));
		assertTrue(Files.isRegularFile(directory.resolve("smart/anc-who/bindings/ConceptMap-ANCPilotSNOMEDBinding.json")));
		assertEquals(Files.size(Path.of("knowledge-bases/smart/anc-who/PlanDefinition-ANCDT01.json")),
				Files.size(directory.resolve("smart/anc-who/PlanDefinition-ANCDT01.json")));
	}

	@Test
	void keepsAConfiguredDirectory() {
		StandardEnvironment environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(BundledKnowledgeBase.PROPERTY, "knowledge-bases")));

		new BundledKnowledgeBase().postProcessEnvironment(environment, new SpringApplication());

		assertEquals("knowledge-bases", environment.getProperty(BundledKnowledgeBase.PROPERTY));
	}
}
