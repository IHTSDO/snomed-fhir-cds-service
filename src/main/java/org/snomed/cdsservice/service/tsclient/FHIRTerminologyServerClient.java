package org.snomed.cdsservice.service.tsclient;

import org.hl7.fhir.r4.model.Coding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.cdsservice.model.CDSCoding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static java.lang.String.format;

@Service
public class FHIRTerminologyServerClient {

	@Value("${fhir.terminology-server.url}")
	private String terminologyServerUrl;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final Map<String, Collection<Coding>> valueSetCache = new HashMap<>();

	public Collection<Coding> expandValueSet(String valueSetURI) {

		if (!valueSetCache.containsKey(valueSetURI)) {
			logger.info("Expanding ValueSet {}", valueSetURI);

			RestTemplate restTemplate = new RestTemplateBuilder()
					.rootUri(terminologyServerUrl)
					.build();
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
			System.out.println(codings.size());
			valueSetCache.put(valueSetURI, codings.stream().map(cdsCoding -> new Coding(cdsCoding.getSystem(), cdsCoding.getCode(), null)).collect(Collectors.toList()));
		}
		return valueSetCache.get(valueSetURI);
	}

}
