package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Library;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.testsupport.DiagnosticTerminologyFixture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Where a content set keeps a Library's CQL source next to it ({@code <name>.cql} beside {@code Library-<name>.json}),
 * the CQL embedded in the Library must be identical, so the reviewed source is the one that runs.
 */
class SmartContentConsistencyTest {

	/**
	 * Every ECL value set in the CQL content must have a stored expansion, so the content loads pinned and without a
	 * terminology server, and that expansion must contain exactly the codes of the reviewed terminology fixture.
	 */
	@Test
	void everyEclValueSetHasAStoredExpansionMatchingTheFixture() throws Exception {
		List<Path> contentSets;
		try (Stream<Path> dirs = Files.list(Path.of("knowledge-bases/smart"))) {
			contentSets = dirs.filter(Files::isDirectory).sorted().toList();
		}
		for (Path contentSet : contentSets) {
			PlanDefinitionEngine engine = PlanDefinitionEngine.fromDirectory(FhirContext.forR4Cached(), contentSet);
			StoredExpansions stored = StoredExpansions.load(FhirContext.forR4Cached(), contentSet);
			for (Map.Entry<String, String> entry : EclValueSetPreloader.findEclValueSets(engine.getKnowledgeArtifacts()).entrySet()) {
				StoredExpansions.Expansion expansion = stored.find(entry.getKey(), entry.getValue())
						.orElseThrow(() -> new AssertionError("No stored expansion for " + entry.getKey() + " in " + contentSet));
				List<Coding> fixture = DiagnosticTerminologyFixture.EXPANSIONS.get(entry.getValue());
				assertNotNull(fixture, "No terminology fixture expansion for ECL " + entry.getValue());
				assertEquals(fixture.stream().map(Coding::getCode).collect(Collectors.toSet()),
						expansion.codings().stream().map(Coding::getCode).collect(Collectors.toSet()), entry.getKey());
				assertEquals(DiagnosticTerminologyFixture.CODE_SYSTEM_VERSION, expansion.codeSystemVersion(), entry.getKey());
			}
		}
	}

	@Test
	void embeddedCqlMatchesSourceFiles() throws IOException {
		List<Path> sources;
		try (Stream<Path> files = Files.walk(Path.of("knowledge-bases/smart"))) {
			sources = files.filter(file -> file.getFileName().toString().endsWith(".cql")).sorted().toList();
		}
		assertFalse(sources.isEmpty(), "expected CQL sources under knowledge-bases/smart");
		for (Path source : sources) {
			String name = source.getFileName().toString().replaceFirst("\\.cql$", "");
			Path libraryFile = source.resolveSibling("Library-" + name + ".json");
			Library library = FhirContext.forR4Cached().newJsonParser().parseResource(Library.class, Files.readString(libraryFile));
			String embedded = library.getContent().stream()
					.filter(content -> "text/cql".equals(content.getContentType()))
					.map(Attachment::getData).map(data -> new String(data, StandardCharsets.UTF_8))
					.findFirst().orElseThrow();
			assertEquals(Files.readString(source), embedded, libraryFile + " is out of date with " + source);
		}
	}
}
