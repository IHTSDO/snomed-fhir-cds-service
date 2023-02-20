package org.snomed.cdshooks.model;

public class CDSSource {

	private final String label;
	private String url;
	private String icon;
	private Coding topic;

	public CDSSource(String label) {
		this.label = label;
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

	public Coding getTopic() {
		return topic;
	}

	public void setTopic(Coding topic) {
		this.topic = topic;
	}
}
