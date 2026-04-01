package org.snomed.cdsservice.rest.pojo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CDSRequest {

	// The hook that triggered this CDS Service call. See Hooks (https://cds-hooks.hl7.org/2.0/#hooks).
	private String hook;

	// A universally unique identifier (UUID) for this particular hook call.
	private String hookInstance;

	// Hook-specific contextual data that the CDS service will need.
	// For example, with the patient-view hook this will include the FHIR id of the Patient being viewed.
	private Map<String, Object> context;

	// The FHIR data that was prefetched by the CDS Client.
	private Map<String, Object> prefetch;

	private Map<String, String> prefetchStrings;

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

	public void setHook(String hook) {
		this.hook = hook;
	}

	public String getHookInstance() {
		return hookInstance;
	}

	public void setHookInstance(String hookInstance) {
		this.hookInstance = hookInstance;
	}

	public Map<String, Object> getContext() {
		return context;
	}

	public void setContext(Map<String, Object> context) {
		this.context = context;
	}

	public String getContextString(String name) {
		if (context == null) {
			return null;
		}
		Object value = context.get(name);
		return value instanceof String ? (String) value : null;
	}

	@SuppressWarnings("unchecked")
	public List<String> getContextStringList(String name) {
		if (context == null) {
			return null;
		}
		Object value = context.get(name);
		if (!(value instanceof List<?> listValue)) {
			return null;
		}
		if (listValue.stream().allMatch(item -> item instanceof String)) {
			return (List<String>) listValue;
		}
		return null;
	}

	public String getContextValueAsJson(String name, ObjectMapper mapper) throws JsonProcessingException {
		if (context == null) {
			return null;
		}
		Object value = context.get(name);
		return value == null ? null : mapper.writeValueAsString(value);
	}

	public Map<String, Object> getPrefetch() {
		return prefetch;
	}

	public void setPrefetch(Map<String, Object> prefetch) {
		this.prefetch = prefetch;
	}

	public Map<String, String> getPrefetchStrings() {
		return prefetchStrings;
	}

	public void setPrefetchStrings(Map<String, String> prefetchStrings) {
		this.prefetchStrings = prefetchStrings;
	}
}
