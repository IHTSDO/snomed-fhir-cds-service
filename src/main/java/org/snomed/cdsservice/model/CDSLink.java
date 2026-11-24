package org.snomed.cdsservice.model;

/**
 * A CDS Hooks card link: a URL the clinician can open for more information (type {@code absolute}).
 */
public class CDSLink {

	private final String label;
	private final String url;
	private final String type = "absolute";

	public CDSLink(String label, String url) {
		this.label = label;
		this.url = url;
	}

	public String getLabel() {
		return label;
	}

	public String getUrl() {
		return url;
	}

	public String getType() {
		return type;
	}
}
