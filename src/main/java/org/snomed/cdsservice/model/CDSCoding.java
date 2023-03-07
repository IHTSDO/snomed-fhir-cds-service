package org.snomed.cdsservice.model;

import java.util.Objects;

public class CDSCoding {

	private String system;
	private String code;
	private String display;

	public CDSCoding() {
	}

	public CDSCoding(String system, String code) {
		this.system = system;
		this.code = code;
	}

	public CDSCoding(String system, String code, String display) {
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CDSCoding cdsCoding = (CDSCoding) o;
		return Objects.equals(system, cdsCoding.system) && Objects.equals(code, cdsCoding.code);
	}

	@Override
	public int hashCode() {
		return Objects.hash(system, code);
	}
}
