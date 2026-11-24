package org.snomed.cdsservice.rest.pojo;

public class VersionInfo {

	private final String version;
	private final String buildTimestamp;

	public VersionInfo(String version, String buildTimestamp) {
		this.version = version;
		this.buildTimestamp = buildTimestamp;
	}

	public String getVersion() {
		return version;
	}

	public String getBuildTimestamp() {
		return buildTimestamp;
	}
}
