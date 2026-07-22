package org.snomed.cdsservice.service.rules;

import org.hl7.fhir.r4.model.Coding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.rules.Criterion;
import org.snomed.cdsservice.model.rules.CriterionType;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.util.SnomedValueSetUtil;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves criterion {@code code_selector} ECL to concrete codes, treating a bare concept or a
 * disjunction of bare concepts as a server-free literal set (see {@link EclSelector}). Any ECL that uses
 * operators is expanded once against the FHIR terminology server and cached.
 * <p>
 * Expansion happens at startup via {@link #warmUp}: an enabled criterion whose ECL cannot be resolved
 * locally and cannot be expanded fails the application fast, rather than silently producing no matches.
 * Because the supplied demonstration content uses only enumerated concepts, no expansion is attempted and
 * the service starts without a terminology server.
 */
@Component
public class EclCodeResolver implements CodeResolver {

	private final FHIRTerminologyServerClient terminologyServerClient;
	private final ConcurrentHashMap<String, Set<CodeKey>> serverExpansionCache = new ConcurrentHashMap<>();
	private final Logger logger = LoggerFactory.getLogger(getClass());

	public EclCodeResolver(FHIRTerminologyServerClient terminologyServerClient) {
		this.terminologyServerClient = terminologyServerClient;
	}

	@Override
	public Set<CodeKey> acceptableCodes(Criterion criterion) {
		EclSelector.Resolution resolution = EclSelector.parse(criterion.codeSelector());
		if (resolution.resolvableLocally()) {
			return resolution.codes();
		}
		Set<CodeKey> expansion = serverExpansionCache.get(resolution.ecl());
		if (expansion == null) {
			throw new IllegalStateException(("ECL '%s' for criterion '%s' was not expanded at startup. "
					+ "Operator-based selectors must be warmed up before evaluation.").formatted(resolution.ecl(), criterion.criterionId()));
		}
		return expansion;
	}

	/**
	 * Expand and cache every operator-based ECL selector used by the supplied criteria. Fails fast with a
	 * {@link ServiceException} when a required expansion cannot be resolved.
	 */
	public void warmUp(Collection<Criterion> criteria) throws ServiceException {
		for (Criterion criterion : criteria) {
			if (criterion.criterionType() == CriterionType.CONTEXT_VALUE) {
				continue;
			}
			String selector = criterion.codeSelector();
			if (selector == null || selector.isBlank()) {
				continue;
			}
			EclSelector.Resolution resolution = EclSelector.parse(selector);
			if (!resolution.resolvableLocally()) {
				expandAndCache(resolution.ecl(), criterion);
			}
		}
	}

	private void expandAndCache(String ecl, Criterion criterion) throws ServiceException {
		if (serverExpansionCache.containsKey(ecl)) {
			return;
		}
		try {
			Collection<Coding> codings = terminologyServerClient.expandValueSet(SnomedValueSetUtil.getSnomedECLValueSetURI(ecl));
			Set<CodeKey> codes = new LinkedHashSet<>();
			for (Coding coding : codings) {
				codes.add(CodeKey.of(coding));
			}
			if (codes.isEmpty()) {
				throw new ServiceException("ECL '%s' for criterion '%s' expanded to no concepts.".formatted(ecl, criterion.criterionId()));
			}
			serverExpansionCache.put(ecl, codes);
			logger.info("Expanded ECL '{}' for criterion '{}' to {} concept(s).", ecl, criterion.criterionId(), codes.size());
		} catch (ServiceException e) {
			throw e;
		} catch (Exception e) {
			throw new ServiceException("Failed to expand ECL '%s' for criterion '%s' against the terminology server.".formatted(ecl, criterion.criterionId()), e);
		}
	}
}
