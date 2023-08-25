package org.snomed.cdsservice.service;

import static java.lang.String.format;

public class ArgumentAssertionUtil {

	public static void expectNotNull(Object object, String name) {
		if (object == null) {
			throw new IllegalArgumentException(format("'%s' is expected, it must not be null.", name));
		}
	}

	public static void expectCount(int expectedSize, int actualSize, String name) {
		if (expectedSize != actualSize) {
			throw new IllegalArgumentException(format("Expected %s of '%s' but found %s.", expectedSize, name, actualSize));
		}
	}
}
