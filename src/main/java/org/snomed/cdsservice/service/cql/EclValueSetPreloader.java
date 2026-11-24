package org.snomed.cdsservice.service.cql;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Attachment;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.rules.EclCodeResolver.EclExpansion;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes SNOMED CT ECL value sets usable by the CQL engine, which cannot expand ECL by itself.
 * <p>
 * Two forms are recognised in the knowledge content:
 * <ul>
 *     <li>ValueSet resources whose SNOMED CT includes use the standard ECL filter
 *     ({@code property = constraint}, {@code op = =}), optionally alongside enumerated SNOMED CT concepts;</li>
 *     <li>implicit ECL value set URLs ({@code http://snomed.info/sct?fhir_vs=ecl/<ECL>}) referenced directly from
 *     CQL {@code valueset} declarations.</li>
 * </ul>
 * Each ECL is preloaded into the engine together with the SNOMED CT version of its expansion. With
 * {@link StoredExpansions}, the stored (reviewed, version-pinned) expansion is used and the terminology server is only
 * needed for value sets that have none, or when refreshing. As for the TSV rules, an ECL that cannot be expanded
 * (or is empty, or exceeds the size limit) fails loading rather than silently matching nothing.
 */
public class EclValueSetPreloader {

	static final String SNOMED = "http://snomed.info/sct";
	static final String IMPLICIT_ECL_PREFIX = SNOMED + "?fhir_vs=ecl/";

	private static final Pattern IMPLICIT_ECL_URL = Pattern.compile("'(" + Pattern.quote(IMPLICIT_ECL_PREFIX) + "[^']+)'");

	/** Expands an ECL expression, failing when it cannot (see {@code EclCodeResolver#expand}). */
	@FunctionalInterface
	public interface EclExpander {
		EclExpansion expand(String ecl) throws ServiceException;
	}

	private final EclExpander eclExpander;
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public EclValueSetPreloader(EclExpander eclExpander) {
		this.eclExpander = eclExpander;
	}

	/** Expands every ECL value set through the terminology server. */
	public void preload(PlanDefinitionEngine engine) throws ServiceException {
		for (Map.Entry<String, String> entry : findEclValueSets(engine.getKnowledgeArtifacts()).entrySet()) {
			EclExpansion expansion = expand(entry.getKey(), entry.getValue());
			engine.preloadValueSetExpansion(entry.getKey(), expansion.codings(), expansion.codeSystemVersion());
			log(entry.getKey(), expansion.codings().size(), expansion.codeSystemVersion(), "terminology server");
		}
	}

	/**
	 * Preloads every ECL value set from its stored expansion. A value set without one is expanded through the
	 * terminology server, with a warning. With {@code refresh}, every value set is expanded through the terminology
	 * server and its stored expansion rewritten.
	 */
	public void preload(PlanDefinitionEngine engine, StoredExpansions stored, boolean refresh) throws ServiceException {
		for (Map.Entry<String, String> entry : findEclValueSets(engine.getKnowledgeArtifacts()).entrySet()) {
			String url = entry.getKey();
			String ecl = entry.getValue();
			Optional<StoredExpansions.Expansion> storedExpansion = refresh ? Optional.empty() : stored.find(url, ecl);
			if (storedExpansion.isPresent()) {
				StoredExpansions.Expansion expansion = storedExpansion.get();
				engine.preloadValueSetExpansion(url, expansion.codings(), expansion.codeSystemVersion());
				log(url, expansion.codings().size(), expansion.codeSystemVersion(), "stored expansion");
				continue;
			}
			EclExpansion expansion = expand(url, ecl);
			engine.preloadValueSetExpansion(url, expansion.codings(), expansion.codeSystemVersion());
			if (refresh) {
				try {
					stored.save(url, ecl, expansion.codings(), expansion.codeSystemVersion());
				} catch (IOException e) {
					throw new ServiceException("Failed to store the expansion of value set '%s'.".formatted(url), e);
				}
				log(url, expansion.codings().size(), expansion.codeSystemVersion(), "terminology server, stored");
			} else {
				logger.warn("No stored expansion for value set '{}'; expanded it through the terminology server (SNOMED CT {}). "
						+ "Run with terminology.expansions.refresh=true to store and pin it.", url, expansion.codeSystemVersion());
			}
		}
	}

	private EclExpansion expand(String url, String ecl) throws ServiceException {
		try {
			return eclExpander.expand(ecl);
		} catch (ServiceException e) {
			throw new ServiceException("Value set '%s' could not be prepared: %s".formatted(url, e.getMessage()), e);
		}
	}

	private void log(String url, int size, String version, String source) {
		logger.info("Preloaded ECL value set '{}' with {} concept(s) (SNOMED CT {}, from {}).", url, size,
				version == null ? "not applicable: resolved locally" : version, source);
	}

	/**
	 * @return the ECL behind every ECL-based value set in the content, keyed by the URL the engine will look it up
	 * by (for ValueSet resources, both {@code url} and {@code url|version})
	 */
	static Map<String, String> findEclValueSets(List<IBaseResource> artifacts) throws ServiceException {
		Map<String, String> eclByUrl = new LinkedHashMap<>();
		for (IBaseResource artifact : artifacts) {
			if (artifact instanceof ValueSet valueSet) {
				String ecl = eclOf(valueSet);
				if (ecl != null) {
					eclByUrl.put(valueSet.getUrl(), ecl);
					if (valueSet.hasVersion()) {
						eclByUrl.put(valueSet.getUrl() + "|" + valueSet.getVersion(), ecl);
					}
				}
			} else if (artifact instanceof Library library) {
				for (Attachment content : library.getContent()) {
					if ("text/cql".equals(content.getContentType()) && content.hasData()) {
						Matcher matcher = IMPLICIT_ECL_URL.matcher(new String(content.getData(), StandardCharsets.UTF_8));
						while (matcher.find()) {
							String url = matcher.group(1);
							eclByUrl.put(url, URLDecoder.decode(url.substring(IMPLICIT_ECL_PREFIX.length()), StandardCharsets.UTF_8));
						}
					}
				}
			}
		}
		return eclByUrl;
	}

	/**
	 * @return the ECL equivalent of the ValueSet's compose, or null if it uses no ECL filter
	 */
	static String eclOf(ValueSet valueSet) throws ServiceException {
		ValueSet.ValueSetComposeComponent compose = valueSet.getCompose();
		boolean usesEcl = compose.getInclude().stream().anyMatch(include -> SNOMED.equals(include.getSystem())
				&& include.getFilter().stream().anyMatch(EclValueSetPreloader::isEclFilter));
		if (!usesEcl) {
			return null;
		}
		if (compose.hasExclude()) {
			throw new ServiceException("ValueSet '%s': excludes are not supported alongside ECL; express them in the ECL with MINUS.".formatted(valueSet.getUrl()));
		}
		List<String> parts = new ArrayList<>();
		for (ValueSet.ConceptSetComponent include : compose.getInclude()) {
			if (!SNOMED.equals(include.getSystem()) || include.hasValueSet()) {
				throw new ServiceException("ValueSet '%s': an ECL value set may only include SNOMED CT concepts and ECL filters.".formatted(valueSet.getUrl()));
			}
			for (ValueSet.ConceptSetFilterComponent filter : include.getFilter()) {
				if (!isEclFilter(filter)) {
					throw new ServiceException("ValueSet '%s': unsupported SNOMED CT filter '%s'.".formatted(valueSet.getUrl(), filter.getProperty()));
				}
				parts.add(filter.getValue());
			}
			include.getConcept().forEach(concept -> parts.add(concept.getCode()));
		}
		return parts.size() == 1 ? parts.get(0) : String.join(" OR ", parts.stream().map(part -> "(" + part + ")").toList());
	}

	private static boolean isEclFilter(ValueSet.ConceptSetFilterComponent filter) {
		return "constraint".equals(filter.getProperty()) && filter.getOp() == ValueSet.FilterOperator.EQUAL;
	}
}
