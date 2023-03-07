package org.snomed.cdsservice.model;

public class CDSSource {

	private final String label;
	private String url;
	private String icon;
	private CDSCoding topic;

	public CDSSource(String label) {
		this.label = label;
	}

	public CDSSource(String label, String url) {
		this.label = label;
		this.url = url;
	}

	public String getLabel() {
		return label;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getIcon() {
		return icon;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	public CDSCoding getTopic() {
		return topic;
	}

	public void setTopic(CDSCoding topic) {
		this.topic = topic;
	}
}
