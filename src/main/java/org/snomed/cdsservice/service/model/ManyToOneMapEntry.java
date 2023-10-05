package org.snomed.cdsservice.service.model;

import java.util.Set;

public class ManyToOneMapEntry {

	private final Set<String> sourceCodes;
	private final String targetCode;
	private final int mapPriority;
	private final String label;

	public ManyToOneMapEntry(Set<String> sourceCodes, String targetCode, int mapPriority, String label) {
		this.sourceCodes = sourceCodes;
		this.targetCode = targetCode;
		this.mapPriority = mapPriority;
		this.label = label;
	}


	public Set<String> getSourceCodes() {
		return sourceCodes;
	}

	public String getTargetCode() {
		return targetCode;
	}

	public int getMapPriority() {
		return mapPriority;
	}

	public String getLabel() {
		return label;
	}

}
