package org.snomed.cdsservice.service.tsclient;

import org.hl7.fhir.r4.model.Parameters;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConceptParameters extends Parameters {

	private static final Logger logger = LoggerFactory.getLogger(ConceptParameters.class);

	public SnomedConceptNormalForm getNormalForm() {
		SnomedConceptNormalForm normalForm = new SnomedConceptNormalForm();
		String normalFormString = getPropertyValue("normalFormTerse");

		if (normalFormString == null) {
			String errorMessage = "No 'normalFormTerse' property found in response from FHIR Termionlogy Server.";
			logger.error(errorMessage);
			throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, errorMessage, null);
		}

		// Remove definition status (=== or <<<)
		normalFormString = normalFormString.replace("===", "");
		normalFormString = normalFormString.replace("<<<", "");

		String[] colonSplit = normalFormString.split(":", 2);
		String parents = colonSplit[0];
		String attributesAndGroups = colonSplit[1];

		String[] parentConcepts = parents.split(" + ");
		for (String parentConcept : parentConcepts) {
			normalForm.addParent(parentConcept.trim());
		}

		Pattern attributePattern = Pattern.compile("(\\d+) ?= ?#?([\\d.]+)");
		String ungroupedAttributes;
		if (attributesAndGroups.contains("{")) {
			ungroupedAttributes = attributesAndGroups.substring(0,attributesAndGroups.indexOf("{"));
		} else {
			ungroupedAttributes = attributesAndGroups;
		}

		Map<String, String> attributes = normalForm.getAttributes();
		Matcher attributeMatcher = attributePattern.matcher(ungroupedAttributes);
		while (attributeMatcher.find()) {
			attributes.put(attributeMatcher.group(1), attributeMatcher.group(2));
		}

		Pattern groupPattern = Pattern.compile("\\{([^}]*)}");
		Matcher groupMatcher = groupPattern.matcher(attributesAndGroups);
		while (groupMatcher.find()) {
			String group = groupMatcher.group(0);
			Map<String, String> groupAttributes = new HashMap<>();
			Matcher groupedAttributeMatcher = attributePattern.matcher(group);
			while (groupedAttributeMatcher.find()) {
				groupAttributes.put(groupedAttributeMatcher.group(1), groupedAttributeMatcher.group(2));
			}
			normalForm.getAttributeGroups().add(groupAttributes);
		}

		return normalForm;
	}

	@Nullable
	public String getPropertyValue(String propertyName) {
		List<ParametersParameterComponent> properties = getParameters("property");
		for (Parameters.ParametersParameterComponent property : properties) {
			boolean correctPart = false;
			for (Parameters.ParametersParameterComponent part : property.getPart()) {
				if (correctPart && "valueString".equals(part.getName())) {
					return part.getValue().toString();
				}
				if ("code".equals(part.getName()) && propertyName.equals(part.getValue().toString())) {
					correctPart = true;
				}
			}
		}
		return null;
	}
}
