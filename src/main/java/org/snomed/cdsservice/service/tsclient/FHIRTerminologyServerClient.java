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

	private final RestTemplate restTemplate;
	private final Map<String, ConceptParameters> lookupCache = new HashMap<>();
	private final Map<String, Collection<Coding>> valueSetCache = new HashMap<>();

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public FHIRTerminologyServerClient(@Value("${fhir.terminology-server.url}") String terminologyServerUrl) {
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

}
