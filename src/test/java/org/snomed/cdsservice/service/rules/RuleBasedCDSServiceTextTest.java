package org.snomed.cdsservice.service.rules;

import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.model.rules.RuleSet;
import org.snomed.cdsservice.service.ServiceException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The discovery title and description of a TSV diagnostic service are generated from its rules, so a user browsing
 * services sees what the rules suggest, through which pathways, and from which guideline.
 */
class RuleBasedCDSServiceTextTest {

	@Test
	void diabetesTexts() throws ServiceException {
		RuleSet diabetes = new RuleLoaderService().loadRuleSet("diabetes", "knowledge-bases/diabetes_criteria.tsv", "knowledge-bases/diabetes_rules.tsv");

		assertEquals("Diagnostic support: type 2 diabetes mellitus (TSV rules)", RuleBasedCDSService.title(diabetes));
		assertEquals("Suggests or confirms type 2 diabetes mellitus from 5 pathways: fasting blood glucose; oral glucose tolerance test; "
				+ "random plasma glucose with symptoms or crisis; portable glucometer fasting; portable glucometer random pathway with symptoms. "
				+ "Source: Guidelines for the Clinical Management of Type 2 Diabetes in Jamaica, Ministry of Health and Wellness, December 2024. "
				+ "Editable TSV rules evaluated by this service's own engine.", RuleBasedCDSService.description(diabetes));
	}
}
