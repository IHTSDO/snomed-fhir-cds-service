package org.snomed.cdsservice.rest.pojo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Bundle;

import java.util.HashMap;
import java.util.Map;

public class CDSRequest {

	// The hook that triggered this CDS Service call. See Hooks (https://cds-hooks.hl7.org/2.0/#hooks).
	private String hook;

	// A universally unique identifier (UUID) for this particular hook call.
	private String hookInstance;

	// Hook-specific contextual data that the CDS service will need.
	// For example, with the patient-view hook this will include the FHIR id of the Patient being viewed.
	private Map<String, String> context;

	// The FHIR data that was prefetched by the CDS Client.
	private Map<String, Object> prefetch;

	private Map<String, String> prefetchStrings;

	private Bundle prefetchBundle;

	public void populatePrefetchStrings(ObjectMapper mapper) throws JsonProcessingException {
		if (prefetch == null) {
			return;
		}
		prefetchStrings = new HashMap<>();
		for (Map.Entry<String, Object> entry : prefetch.entrySet()) {
			prefetchStrings.put(entry.getKey(), mapper.writeValueAsString(entry.getValue()));
		}
	}

	public String getHook() {
		return hook;
	}

	public String getHookInstance() {
		return hookInstance;
	}

	public Map<String, String> getContext() {
		return context;
	}

	public Map<String, Object> getPrefetch() {
		return prefetch;
	}

	public Map<String, String> getPrefetchStrings() {
		return prefetchStrings;
	}
}
