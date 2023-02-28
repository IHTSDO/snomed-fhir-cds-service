package org.snomed.cdsservice.model;

import java.util.UUID;

public class CDSCard {

	private final String uuid;

	// Must be less than 140 characters
	private final String summary;

	// Must use GitHub Flavoured Markdown, see https://github.github.com/gfm/
	private String detail;

	private final CDSIndicator indicator;

	private final CDSSource source;

	public CDSCard(String summary, CDSIndicator indicator, CDSSource source) {
		this.uuid = UUID.randomUUID().toString();
		this.summary = summary;
		this.indicator = indicator;
		this.source = source;
	}

	public CDSCard(String summary, String detail, CDSIndicator indicator, CDSSource source) {
		this(summary, indicator, source);
		this.detail = detail;
	}

	public String getUuid() {
		return uuid;
	}

	public String getSummary() {
		return summary;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}

	public CDSIndicator getIndicator() {
		return indicator;
	}

	public CDSSource getSource() {
		return source;
	}
}
