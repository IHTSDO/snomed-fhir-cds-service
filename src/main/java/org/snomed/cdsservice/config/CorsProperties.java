package org.snomed.cdsservice.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

	private boolean enabled = false;

	private List<String> allowedOriginPatterns = new ArrayList<>();

	private List<String> allowedMethods = List.of("GET", "POST", "OPTIONS");

	private List<String> allowedHeaders = List.of("*");

	// Answers Private Network Access preflights, so a frontend served from a public site can call a service
	// running on localhost or a private network (Chromium browsers).
	private boolean allowPrivateNetwork = false;

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public List<String> getAllowedOriginPatterns() {
		return allowedOriginPatterns;
	}

	public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
		this.allowedOriginPatterns = allowedOriginPatterns;
	}

	public List<String> getAllowedMethods() {
		return allowedMethods;
	}

	public void setAllowedMethods(List<String> allowedMethods) {
		this.allowedMethods = allowedMethods;
	}

	public List<String> getAllowedHeaders() {
		return allowedHeaders;
	}

	public void setAllowedHeaders(List<String> allowedHeaders) {
		this.allowedHeaders = allowedHeaders;
	}

	public boolean isAllowPrivateNetwork() {
		return allowPrivateNetwork;
	}

	public void setAllowPrivateNetwork(boolean allowPrivateNetwork) {
		this.allowPrivateNetwork = allowPrivateNetwork;
	}
}
