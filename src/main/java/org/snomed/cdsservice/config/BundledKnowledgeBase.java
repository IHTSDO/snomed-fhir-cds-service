package org.snomed.cdsservice.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Uses the knowledge base bundled in the jar when {@value #PROPERTY} is not set.
 * <p>
 * The rule tables and the FHIR content ({@code knowledge-bases/}) are packaged in the jar, so deploying the jar
 * deploys the content that was built and tested with the code. The services read files, so at startup the bundled
 * copy is extracted to a temporary directory, deleted on shutdown, and {@value #PROPERTY} points at it. Setting
 * {@value #PROPERTY} to a directory (e.g. {@code knowledge-bases} in the local profile) uses those files instead.
 * <p>
 * Runs before the configuration is read (an {@link EnvironmentPostProcessor}): the rule paths are
 * {@code ${knowledge-base.directory}/...}, and the PlanDefinition and diagnostic service registrars read them while
 * the application context is being defined.
 */
public class BundledKnowledgeBase implements EnvironmentPostProcessor, Ordered {

	public static final String PROPERTY = "knowledge-base.directory";
	static final String CLASSPATH_ROOT = "knowledge-bases/";

	@Override
	public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
		String configured = environment.getProperty(PROPERTY);
		if (configured != null && !configured.isBlank()) {
			return;
		}
		Path directory = extract(environment.getClass().getClassLoader());
		environment.getPropertySources().addFirst(new MapPropertySource("bundledKnowledgeBase",
				Map.of(PROPERTY, directory.toString())));
	}

	/** Extracts the bundled knowledge base to a new temporary directory, deleted when the JVM exits. */
	static Path extract(ClassLoader classLoader) {
		try {
			Path directory = Files.createTempDirectory("cds-knowledge-base-");
			Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteRecursively(directory)));
			Resource[] resources = new PathMatchingResourcePatternResolver(classLoader).getResources("classpath*:" + CLASSPATH_ROOT + "**");
			int files = 0;
			for (Resource resource : resources) {
				String url = resource.getURL().toString();
				int start = url.lastIndexOf("/" + CLASSPATH_ROOT);
				String relative = start < 0 ? "" : url.substring(start + CLASSPATH_ROOT.length() + 1);
				if (relative.isEmpty() || relative.endsWith("/") || !resource.isReadable()) {
					continue;
				}
				Path target = directory.resolve(URLDecoder.decode(relative, StandardCharsets.UTF_8)).normalize();
				if (!target.startsWith(directory)) {
					continue;
				}
				Files.createDirectories(target.getParent());
				try (InputStream in = resource.getInputStream()) {
					Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
				}
				files++;
			}
			if (files == 0) {
				throw new IllegalStateException("No knowledge base is bundled in the application (classpath:" + CLASSPATH_ROOT
						+ "), and " + PROPERTY + " is not set.");
			}
			return directory;
		} catch (IOException e) {
			throw new UncheckedIOException("Could not extract the bundled knowledge base.", e);
		}
	}

	private static void deleteRecursively(Path directory) {
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		} catch (IOException ignored) {
			// Best effort: the directory is in the system's temporary directory.
		}
	}

	@Override
	public int getOrder() {
		// After the application's property files are loaded, so a configured directory is seen.
		return Ordered.LOWEST_PRECEDENCE;
	}
}
