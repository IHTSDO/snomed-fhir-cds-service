package org.snomed.cdshooks.model;

public class Coding {

	private final String system;
	private final String code;
	private String display;

	public Coding(String system, String code) {
		this.system = system;
		this.code = code;
	}

	public Coding(String system, String code, String display) {
		this.system = system;
		this.code = code;
		this.display = display;
	}

	public String getSystem() {
		return system;
	}

	public String getCode() {
		return code;
	}

	public String getDisplay() {
		return display;
	}

}
