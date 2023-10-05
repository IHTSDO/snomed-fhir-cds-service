package org.snomed.cdsservice.service.tsclient;

import java.util.*;

public class SnomedConceptNormalForm {

	private final Set<String> parentCodes;
	private final Map<String, String> attributes;
	private final List<Map<String, String>> attributeGroups;

	public SnomedConceptNormalForm() {
		parentCodes = new HashSet<>();
		attributes = new HashMap<>();
		attributeGroups = new ArrayList<>();
	}

	public void addParent(String code) {
		parentCodes.add(code);
	}

	public Set<String> getParentCodes() {
		return parentCodes;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public List<Map<String, String>> getAttributeGroups() {
		return attributeGroups;
	}
}
