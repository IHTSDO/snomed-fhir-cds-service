package org.snomed.cdsservice.service.tsclient;

import org.snomed.cdsservice.model.CDSCoding;

import java.util.List;

public class ValueSetExpansion {

	private Integer total;
	private List<CDSCoding> contains;
	private List<ExpansionParameter> parameter;

	public ValueSetExpansion() {
	}

	public Integer getTotal() {
		return total;
	}

	public List<CDSCoding> getContains() {
		return contains;
	}

	public List<ExpansionParameter> getParameter() {
		return parameter;
	}
}
