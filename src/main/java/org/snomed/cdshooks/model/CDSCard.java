package org.snomed.cdshooks.model;

import java.util.UUID;

public class CDSCard {

	private final String uuid;
	private final String summary;
	private String detail;
	private final CDSIndicator indicator;
	private final CDSSource source;

	public CDSCard(String summary, CDSIndicator indicator, CDSSource source) {
		this.uuid = UUID.randomUUID().toString();
		this.summary = summary;
		this.indicator = indicator;
		this.source = source;
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
