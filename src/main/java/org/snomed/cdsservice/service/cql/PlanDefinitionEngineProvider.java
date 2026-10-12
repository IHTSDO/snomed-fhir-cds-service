package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one {@link PlanDefinitionEngine} per content directory, so that all PlanDefinitions loaded from the same
 * content share one content repository and one set of compiled-library and value set caches.
 */
@Service
public class PlanDefinitionEngineProvider {

	@Autowired
	private FhirContext fhirContext;

	private final Map<Path, PlanDefinitionEngine> engines = new ConcurrentHashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public PlanDefinitionEngine getEngine(Path contentDirectory) {
		return engines.computeIfAbsent(contentDirectory.toAbsolutePath().normalize(), directory -> {
			try {
				PlanDefinitionEngine engine = PlanDefinitionEngine.fromDirectory(fhirContext, directory);
				logger.info("Loaded FHIR knowledge artifacts from '{}'.", directory);
				return engine;
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to load FHIR knowledge artifacts from '%s'.".formatted(directory), e);
			}
		});
	}
}
