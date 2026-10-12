package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.rules.EclCodeResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one {@link PlanDefinitionEngine} per content directory, so that all PlanDefinitions loaded from the same
 * content share one content repository and one set of compiled-library and value set caches. SNOMED CT ECL value
 * sets in the content are expanded through the terminology server when the content is loaded.
 */
@Service
public class PlanDefinitionEngineProvider {

	@Autowired
	private FhirContext fhirContext;
	@Autowired
	private EclCodeResolver eclCodeResolver;

	private final Map<Path, PlanDefinitionEngine> engines = new ConcurrentHashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public PlanDefinitionEngine getEngine(Path contentDirectory) {
		return engines.computeIfAbsent(contentDirectory.toAbsolutePath().normalize(), directory -> {
			try {
				PlanDefinitionEngine engine = PlanDefinitionEngine.fromDirectory(fhirContext, directory);
				new EclValueSetPreloader(eclCodeResolver::expand).preload(engine);
				logger.info("Loaded FHIR knowledge artifacts from '{}'.", directory);
				return engine;
			} catch (IOException e) {
				throw new UncheckedIOException("Failed to load FHIR knowledge artifacts from '%s'.".formatted(directory), e);
			} catch (ServiceException e) {
				throw new IllegalStateException("Failed to prepare FHIR knowledge artifacts from '%s'.".formatted(directory), e);
			}
		});
	}
}
