package org.snomed.cdsservice.service.rules;

import org.hl7.fhir.r4.model.Coding;

/**
 * A terminology code identified by its code system and code, used for exact matching against incoming
 * FHIR codings. Display is deliberately ignored so matching depends only on {@code system + code}.
 */
public record CodeKey(String system, String code) {

	public static CodeKey of(Coding coding) {
		return new CodeKey(coding.getSystem(), coding.getCode());
	}
}
