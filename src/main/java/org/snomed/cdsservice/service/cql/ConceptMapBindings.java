package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.ConceptMap;
import org.hl7.fhir.r4.model.Enumerations.ConceptMapEquivalence;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Curated terminology bindings kept next to a content set ({@code <content set>/bindings/}), as FHIR ConceptMaps from
 * the content's own codes to a standard terminology (e.g. WHO SMART ANC codes to SNOMED CT).
 * <p>
 * Each enumerated ValueSet of the content that contains a mapped code is preloaded into the engine with the mapped
 * codes added. The content stays unmodified, and its logic also accepts data coded with the bound terminology.
 * Targets that are {@code unmatched} or {@code disjoint} add nothing; the version recorded is the group's
 * {@code targetVersion} (the release the binding was reviewed against).
 */
public class ConceptMapBindings {

	public static final String DIRECTORY = "bindings";

	private static final Set<ConceptMapEquivalence> NOT_BOUND = Set.of(ConceptMapEquivalence.UNMATCHED, ConceptMapEquivalence.DISJOINT);

	private record Target(Coding coding, String version) {
	}

	private final List<ConceptMap> conceptMaps;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public ConceptMapBindings(List<ConceptMap> conceptMaps) {
		this.conceptMaps = List.copyOf(conceptMaps);
	}

	public static ConceptMapBindings load(FhirContext fhirContext, Path contentDirectory) throws IOException {
		Path directory = contentDirectory.resolve(DIRECTORY);
		List<ConceptMap> conceptMaps = new ArrayList<>();
		if (Files.isDirectory(directory)) {
			try (Stream<Path> files = Files.list(directory)) {
				for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".json")).sorted().toList()) {
					IBaseResource resource = fhirContext.newJsonParser().parseResource(Files.readString(file));
					if (resource instanceof ConceptMap conceptMap) {
						conceptMaps.add(conceptMap);
					}
				}
			}
		}
		return new ConceptMapBindings(conceptMaps);
	}

	/**
	 * Preloads every bound ValueSet of the engine's content with its codes plus their mapped codes.
	 *
	 * @return the canonical URLs of the ValueSets that were bound
	 */
	public Set<String> applyTo(PlanDefinitionEngine engine) {
		Map<String, List<Target>> targets = targets();
		Set<String> bound = new TreeSet<>();
		if (targets.isEmpty()) {
			return bound;
		}
		Map<String, ValueSet> valueSets = new LinkedHashMap<>();
		for (IBaseResource artifact : engine.getKnowledgeArtifacts()) {
			if (artifact instanceof ValueSet valueSet && valueSet.hasUrl()) {
				valueSets.put(valueSet.getUrl(), valueSet);
			}
		}
		for (ValueSet valueSet : valueSets.values()) {
			Optional<List<Coding>> codes = enumerate(valueSet, valueSets, new HashSet<>());
			if (codes.isEmpty()) {
				continue;
			}
			List<Coding> expansion = new ArrayList<>(codes.get());
			Set<String> versions = new LinkedHashSet<>();
			for (Coding coding : codes.get()) {
				for (Target target : targets.getOrDefault(key(coding.getSystem(), coding.getCode()), List.of())) {
					if (expansion.stream().noneMatch(c -> key(c.getSystem(), c.getCode()).equals(key(target.coding().getSystem(), target.coding().getCode())))) {
						expansion.add(target.coding());
					}
					if (target.version() != null) {
						versions.add(target.version());
					}
				}
			}
			if (expansion.size() == codes.get().size()) {
				continue;
			}
			String version = versions.isEmpty() ? null : String.join(", ", versions);
			engine.preloadValueSetExpansion(valueSet.getUrl(), expansion, version);
			if (valueSet.hasVersion()) {
				engine.preloadValueSetExpansion(valueSet.getUrl() + "|" + valueSet.getVersion(), expansion, version);
			}
			bound.add(valueSet.getUrl());
		}
		logger.info("Bound {} value set(s) through {} ConceptMap(s).", bound.size(), conceptMaps.size());
		return bound;
	}

	/** Source system|code → mapped codes. */
	private Map<String, List<Target>> targets() {
		Map<String, List<Target>> targets = new LinkedHashMap<>();
		for (ConceptMap conceptMap : conceptMaps) {
			for (ConceptMap.ConceptMapGroupComponent group : conceptMap.getGroup()) {
				for (ConceptMap.SourceElementComponent element : group.getElement()) {
					for (ConceptMap.TargetElementComponent target : element.getTarget()) {
						if (target.hasCode() && !NOT_BOUND.contains(target.getEquivalence())) {
							targets.computeIfAbsent(key(group.getSource(), element.getCode()), k -> new ArrayList<>())
									.add(new Target(new Coding(group.getTarget(), target.getCode(), target.getDisplay()),
											group.hasTargetVersion() ? group.getTargetVersion() : null));
						}
					}
				}
			}
		}
		return targets;
	}

	/**
	 * The codes of a ValueSet made only of enumerated concepts and of other such ValueSets of the content; empty when
	 * it uses filters or excludes, or includes a ValueSet the content does not have.
	 */
	private static Optional<List<Coding>> enumerate(ValueSet valueSet, Map<String, ValueSet> valueSets, Set<String> visiting) {
		if (!visiting.add(valueSet.getUrl()) || valueSet.getCompose().hasExclude()) {
			return Optional.empty();
		}
		List<Coding> codes = new ArrayList<>();
		for (ValueSet.ConceptSetComponent include : valueSet.getCompose().getInclude()) {
			// Filters, whole code systems, and code systems intersected with ValueSets are not enumerated here.
			if (include.hasFilter() || (include.hasSystem() && (!include.hasConcept() || include.hasValueSet()))) {
				return Optional.empty();
			}
			for (ValueSet.ConceptReferenceComponent concept : include.getConcept()) {
				codes.add(new Coding(include.getSystem(), concept.getCode(), concept.getDisplay()));
			}
			for (var included : include.getValueSet()) {
				ValueSet other = valueSets.get(included.getValue().replaceFirst("\\|.*$", ""));
				Optional<List<Coding>> otherCodes = other == null ? Optional.empty() : enumerate(other, valueSets, visiting);
				if (otherCodes.isEmpty()) {
					return Optional.empty();
				}
				codes.addAll(otherCodes.get());
			}
		}
		visiting.remove(valueSet.getUrl());
		return codes.isEmpty() ? Optional.empty() : Optional.of(codes);
	}

	private static String key(String system, String code) {
		return system + "|" + code;
	}
}
