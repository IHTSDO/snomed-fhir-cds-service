package org.snomed.cdsservice.model;

public class CDSCard {

	private final String uuid;

	private final CDSIndicator indicator;

	// Must be less than 140 characters
	private String summary;

	// Must use GitHub Flavoured Markdown, see https://github.github.com/gfm/
	private String detail;

	private final CDSSource source;

	public CDSCard(String uuid, String summary, String detail, CDSIndicator indicator, CDSSource source) {
		this.uuid = uuid;
		this.summary = summary;
		this.detail = detail;
		this.indicator = indicator;
		this.source = source;
	}

	public String getUuid() {
		return uuid;
	}

	public CDSIndicator getIndicator() {
		return indicator;
	}

	public String getSummary() {
		return summary;
	}

	public void setSummary(String summary) {
		this.summary = summary;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}

	public CDSSource getSource() {
		return source;
	}

	public CDSCard cloneCard() {
		return new CDSCard(uuid, summary, detail, indicator, source);
	}
}
