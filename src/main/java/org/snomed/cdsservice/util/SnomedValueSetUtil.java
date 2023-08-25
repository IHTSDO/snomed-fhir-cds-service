package org.snomed.cdsservice.util;

import org.jetbrains.annotations.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SnomedValueSetUtil {

	// Map of characters that will not be URL encoded to help ECL readability
	private static final Map<String, String> eclDecodeForReadabililtyMap = Stream.of("<", ">", "(", ")", "|", "!")
			.collect(Collectors.toMap(s -> URLEncoder.encode(s, StandardCharsets.UTF_8), Function.identity()));

	/**
	 * Creates a SNOMED CT implicit ValueSet URI that can be expanded against a FHIR terminology server.
	 * @param snomedCodeOrECL This must be either a SNOMED CT code, or an ECL statement prefixed with 'ECL='.
	 * @return The URI of a SNOMED CT implicit ValueSet.
	 */
	public static String getSNOMEDValueSetURI(String snomedCodeOrECL) {
		if (snomedCodeOrECL.startsWith("ECL=")) {
			String ecl = snomedCodeOrECL.substring(4);
			return getSnomedECLValueSetURI(ecl);
		} else {
			// This code and all descendants
			return "http://snomed.info/sct?fhir_vs=isa/" + snomedCodeOrECL;
		}
	}

	@NotNull
	public static String getSnomedECLValueSetURI(String ecl) {
		String encodedECL = URLEncoder.encode(ecl, StandardCharsets.UTF_8);
		for (Map.Entry<String, String> decodeEntry : eclDecodeForReadabililtyMap.entrySet()) {
			encodedECL = encodedECL.replace(decodeEntry.getKey(), decodeEntry.getValue());
		}
		return "http://snomed.info/sct?fhir_vs=ecl/" + encodedECL;
	}

}
