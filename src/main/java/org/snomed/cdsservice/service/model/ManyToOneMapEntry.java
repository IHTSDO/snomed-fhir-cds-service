package org.snomed.cdsservice.service.model;

import java.util.Set;

public class ManyToOneMapEntry {

	private final Set<String> sourceCodes;
	private final String targetCode;
	private final int mapPriority;

	public ManyToOneMapEntry(Set<String> sourceCodes, String targetCode, int mapPriority) {
		this.sourceCodes = sourceCodes;
		this.targetCode = targetCode;
		this.mapPriority = mapPriority;
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
}
