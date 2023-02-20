package org.snomed.cdshooks.model;

import java.util.List;

/**
 * A CDS Service that can be called by an EHR.
 */
public abstract class CDSService {

	// The {id} portion of the URL to this service which is available at
	private final String id;

	// The hook this service should be invoked on.
	private String hook;

	// The human-friendly name of this service.
	private String title;

	// The description of this service.
	private String description;

	// An object containing key/value pairs of FHIR queries that this service is requesting the CDS Client
	// to perform and provide on each service call. The key is a string that describes the type of data
	// being requested and the value is a string representing the FHIR query.
	// See Prefetch Template. https://cds-hooks.hl7.org/2.0/#prefetch-template
	private String prefetch;

	// Human-friendly description of any preconditions for the use of this CDS Service.
	private String usageRequirements;

	public CDSService(String id) {
		this.id = id;
	}

	public abstract List<CDSCard> call();

	public String getId() {
		return id;
	}

	public String getHook() {
		return hook;
	}

	public void setHook(String hook) {
		this.hook = hook;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getPrefetch() {
		return prefetch;
	}

	public void setPrefetch(String prefetch) {
		this.prefetch = prefetch;
	}

	public String getUsageRequirements() {
		return usageRequirements;
	}

	public void setUsageRequirements(String usageRequirements) {
		this.usageRequirements = usageRequirements;
	}
}
