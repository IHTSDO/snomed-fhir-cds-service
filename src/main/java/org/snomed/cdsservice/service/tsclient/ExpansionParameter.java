package org.snomed.cdsservice.service.tsclient;

/**
 * A {@code ValueSet.expansion.parameter}, e.g. the code system {@code version} the server used for the expansion.
 */
public class ExpansionParameter {

	private String name;
	private String valueUri;
	private String valueString;

	public ExpansionParameter() {
	}

	public ExpansionParameter(String name, String valueUri, String valueString) {
		this.name = name;
		this.valueUri = valueUri;
		this.valueString = valueString;
	}

	public String getName() {
		return name;
	}

	public String getValueUri() {
		return valueUri;
	}

	public String getValueString() {
		return valueString;
	}
}
