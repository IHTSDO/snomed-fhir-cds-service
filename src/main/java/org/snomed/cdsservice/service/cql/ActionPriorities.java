package org.snomed.cdsservice.service.cql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.cdsservice.model.CDSIndicator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Curated card indicators kept next to a content set ({@code <content set>/priorities/*.json}), for content whose
 * actions carry no priority (e.g. the WHO SMART ANC decision tables). Each entry gives the CDS Hooks indicator of a
 * decision, identified by its PlanDefinition's canonical URL and the action title:
 * <pre>
 * { "rules": [ { "planDefinition": "http://smart.who.int/anc/PlanDefinition/ANCDT17",
 *                "actions": [ { "title": "Refer urgently to a hospital", "indicator": "critical" } ] } ] }
 * </pre>
 * The content stays unmodified. Actions not listed keep the indicator derived from their priority.
 */
public class ActionPriorities {

	public static final String DIRECTORY = "priorities";
	public static final ActionPriorities NONE = new ActionPriorities(Map.of());

	// planDefinition URL|action title → indicator
	private final Map<String, CDSIndicator> indicators;

	ActionPriorities(Map<String, CDSIndicator> indicators) {
		this.indicators = Map.copyOf(indicators);
	}

	public static ActionPriorities load(Path contentDirectory) throws IOException {
		Path directory = contentDirectory.resolve(DIRECTORY);
		if (!Files.isDirectory(directory)) {
			return NONE;
		}
		ObjectMapper objectMapper = new ObjectMapper();
		Map<String, CDSIndicator> indicators = new HashMap<>();
		try (Stream<Path> files = Files.list(directory)) {
			for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
				for (JsonNode rule : objectMapper.readTree(file.toFile()).path("rules")) {
					String planDefinition = rule.path("planDefinition").asText();
					for (JsonNode action : rule.path("actions")) {
						String title = action.path("title").asText();
						String indicator = action.path("indicator").asText();
						if (planDefinition.isBlank() || title.isBlank()) {
							throw new IOException("Priority entry without planDefinition or title in '%s'.".formatted(file));
						}
						try {
							indicators.put(key(planDefinition, title), CDSIndicator.valueOf(indicator));
						} catch (IllegalArgumentException e) {
							throw new IOException("Unknown indicator '%s' in '%s'.".formatted(indicator, file), e);
						}
					}
				}
			}
		}
		return new ActionPriorities(indicators);
	}

	/** The curated indicator of a decision, if any. */
	public Optional<CDSIndicator> indicator(String planDefinitionUrl, String actionTitle) {
		if (planDefinitionUrl == null || actionTitle == null) {
			return Optional.empty();
		}
		return Optional.ofNullable(indicators.get(key(planDefinitionUrl, actionTitle)));
	}

	public int size() {
		return indicators.size();
	}

	private static String key(String planDefinitionUrl, String actionTitle) {
		return planDefinitionUrl.replaceFirst("\\|.*$", "") + "|" + actionTitle.strip();
	}
}
