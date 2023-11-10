package org.snomed.cdsservice.model;

import java.util.List;
public class CDSCard {

	private final String uuid;

	private final CDSIndicator indicator;

	// Must be less than 140 characters
	private String summary;

	// Must use GitHub Flavoured Markdown, see https://github.github.com/gfm/
	private String detail;

	private final CDSSource source;

	private List<CDSReference> referenceMedications;

	private List<CDSReference> referenceConditions;

	private final String alertType;

	public CDSCard(String uuid, String summary, String detail, CDSIndicator indicator, CDSSource source, List<CDSReference> referenceMedications, List<CDSReference> referenceConditions, String alertType) {
		this.uuid = uuid;
		this.summary = summary;
		this.detail = detail;
		this.indicator = indicator;
		this.source = source;
		this.referenceMedications = referenceMedications;
		this.referenceConditions = referenceConditions;
		this.alertType = alertType;
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


	public List<CDSReference> getReferenceMedications() {
		return referenceMedications;
	}

	public void setReferenceMedications(List<CDSReference> referenceMedications) {
		this.referenceMedications = referenceMedications;
	}

	public List<CDSReference> getReferenceConditions() {
		return referenceConditions;
	}

	public void setReferenceConditions(List<CDSReference> referenceConditions) {
		this.referenceConditions = referenceConditions;
	}

	public CDSCard cloneCard() {
		return new CDSCard(uuid, summary, detail, indicator, source, referenceMedications, referenceConditions, alertType);
	}

	public String getAlertType() {
		return alertType;
	}
}
