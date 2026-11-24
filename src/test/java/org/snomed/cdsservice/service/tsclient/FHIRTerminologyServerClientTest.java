package org.snomed.cdsservice.service.tsclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FHIRTerminologyServerClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void readsSnomedVersionFromExpansionParameters() throws Exception {
		// Shape of a Snowstorm $expand response (trimmed).
		ValueSet valueSet = objectMapper.readValue("""
				{ "resourceType": "ValueSet", "expansion": {
				    "total": 14,
				    "parameter": [ { "name": "version",
				      "valueUri": "http://snomed.info/sct|http://snomed.info/sct/900000000000207008/version/20260701" } ],
				    "contains": [ { "system": "http://snomed.info/sct", "code": "44054006" } ] } }
				""", ValueSet.class);

		assertEquals(Optional.of("http://snomed.info/sct/900000000000207008/version/20260701"),
				FHIRTerminologyServerClient.snomedVersion(valueSet.getExpansion()));
	}

	@Test
	void acceptsUsedCodesystemAndIgnoresOtherSystems() throws Exception {
		ValueSet valueSet = objectMapper.readValue("""
				{ "resourceType": "ValueSet", "expansion": { "parameter": [
				    { "name": "used-codesystem", "valueUri": "http://loinc.org|2.78" },
				    { "name": "used-codesystem", "valueUri": "http://snomed.info/sct|http://snomed.info/sct/83821000000107/version/20260916" } ] } }
				""", ValueSet.class);

		assertEquals(Optional.of("http://snomed.info/sct/83821000000107/version/20260916"),
				FHIRTerminologyServerClient.snomedVersion(valueSet.getExpansion()));
	}

	@Test
	void noVersionWhenTheServerReportsNone() throws Exception {
		ValueSet valueSet = objectMapper.readValue("""
				{ "resourceType": "ValueSet", "expansion": { "total": 0 } }
				""", ValueSet.class);

		assertEquals(Optional.empty(), FHIRTerminologyServerClient.snomedVersion(valueSet.getExpansion()));
	}
}
