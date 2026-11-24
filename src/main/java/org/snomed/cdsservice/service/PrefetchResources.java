package org.snomed.cdsservice.service;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.snomed.cdsservice.rest.pojo.CDSRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Flattens the FHIR resources supplied in a CDS Hooks request's prefetch: each prefetch value may be a single
 * resource or a Bundle (e.g. a search result), and empty or {@code null} values are skipped.
 */
public final class PrefetchResources {

	private PrefetchResources() {
	}

	public static List<Resource> parse(FhirContext fhirContext, CDSRequest cdsRequest) {
		List<Resource> resources = new ArrayList<>();
		Map<String, String> prefetchStrings = cdsRequest.getPrefetchStrings();
		if (prefetchStrings == null) {
			return resources;
		}
		for (String json : prefetchStrings.values()) {
			if (json == null || json.isBlank() || json.equals("null")) {
				continue;
			}
			IBaseResource parsed = fhirContext.newJsonParser().parseResource(json);
			if (parsed instanceof Bundle bundle) {
				for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
					if (entry.hasResource()) {
						resources.add(entry.getResource());
					}
				}
			} else if (parsed instanceof Resource resource) {
				resources.add(resource);
			}
		}
		return resources;
	}
}
