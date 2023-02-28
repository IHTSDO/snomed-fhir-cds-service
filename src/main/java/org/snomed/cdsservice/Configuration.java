package org.snomed.cdsservice;

import ca.uhn.fhir.context.FhirContext;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class Configuration {

	@Bean
	public FhirContext fhirContext() {
		return FhirContext.forR4();
	}

}
