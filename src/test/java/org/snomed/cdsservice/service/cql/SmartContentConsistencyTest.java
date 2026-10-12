package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Library;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Where a content set keeps a Library's CQL source next to it ({@code <name>.cql} beside {@code Library-<name>.json}),
 * the CQL embedded in the Library must be identical, so the reviewed source is the one that runs.
 */
class SmartContentConsistencyTest {

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
