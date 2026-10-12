package org.snomed.cdsservice.service.tsclient;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCoding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class FHIRTerminologyServerClient {

	private static final String SNOMED_SYSTEM = "http://snomed.info/sct";

	private final RestTemplate restTemplate;
	private final Map<String, ConceptParameters> lookupCache = new HashMap<>();
	private final Map<String, Collection<Coding>> valueSetCache = new HashMap<>();
	private final Map<String, String> expansionVersionCache = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRTerminologyServerClient(@Value("${fhir.terminology-server.url}") String terminologyServerUrl) {
		if (terminologyServerUrl.isEmpty()) {
			throw new IllegalArgumentException("Terminology server URL cannot be empty. Please set config item 'fhir.terminology-server.url'.");
		}
		restTemplate = new RestTemplateBuilder()
				.rootUri(terminologyServerUrl)
				.build();
	}

	public ConceptParameters lookup(String codeSystem, String code) {
		String cacheKey = format("%s|%s", codeSystem, code);
		if (!lookupCache.containsKey(cacheKey)) {
			logger.info("Lookup system {} code {}", codeSystem, code);

			ResponseEntity<String> response = restTemplate.exchange(format("/CodeSystem/$lookup?_format=json&system=%s&code=%s&property=*", codeSystem, code),
					HttpMethod.GET, null, String.class);
			Parameters parameters = FhirContext.forR4().newJsonParser().parseResource(Parameters.class, response.getBody());
			ConceptParameters conceptParameters =  new ConceptParameters();
			conceptParameters.setParameter(parameters.getParameter());
			lookupCache.put(cacheKey, conceptParameters);
		}
		return lookupCache.get(cacheKey);
	}

	public Collection<Coding> expandValueSet(String valueSetURI) throws RestClientException {
		if (!valueSetCache.containsKey(valueSetURI)) {
			logger.info("Expanding ValueSet {}", valueSetURI);

			int offset = 0;

			List<CDSCoding> codings = new ArrayList<>();
			boolean moreToLoad = true;
			while (moreToLoad) {
				ResponseEntity<ValueSet> response = restTemplate.exchange(format("/ValueSet/$expand?size=1000&_format=json&offset=%s&url=%s", offset, valueSetURI),
						HttpMethod.GET, null, ValueSet.class);
				ValueSet body = response.getBody();
				ValueSetExpansion expansion = body.getExpansion();
				if (offset == 0) {
					snomedVersion(expansion).ifPresent(version -> expansionVersionCache.put(valueSetURI, version));
				}
				List<CDSCoding> contains = expansion.getContains();
				if (contains != null) {
					codings.addAll(contains);
					Integer total = expansion.getTotal();
					moreToLoad = (total != null && codings.size() < total) || contains.isEmpty();
					offset = codings.size();
				} else {
					moreToLoad = false;
				}
			}
			valueSetCache.put(valueSetURI, codings.stream().map(cdsCoding -> new Coding(cdsCoding.getSystem(), cdsCoding.getCode(), null)).collect(Collectors.toList()));
		}
		return valueSetCache.get(valueSetURI);
	}

	/**
	 * @return the SNOMED CT version (edition and release URI) the server reported for an expansion made by
	 * {@link #expandValueSet}, if it reported one
	 */
	public Optional<String> getExpansionVersion(String valueSetURI) {
		return Optional.ofNullable(expansionVersionCache.get(valueSetURI));
	}

	/**
	 * Reads the SNOMED CT version from the expansion's {@code version} or {@code used-codesystem} parameter,
	 * e.g. {@code http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20260701}.
	 */
	static Optional<String> snomedVersion(ValueSetExpansion expansion) {
		if (expansion == null || expansion.getParameter() == null) {
			return Optional.empty();
		}
		String prefix = SNOMED_SYSTEM + "|";
		return expansion.getParameter().stream()
				.filter(parameter -> "version".equals(parameter.getName()) || "used-codesystem".equals(parameter.getName()))
				.map(parameter -> parameter.getValueUri() != null ? parameter.getValueUri() : parameter.getValueString())
				.filter(value -> value != null && value.startsWith(prefix) && value.length() > prefix.length())
				.map(value -> value.substring(prefix.length()))
				.findFirst();
	}

}
