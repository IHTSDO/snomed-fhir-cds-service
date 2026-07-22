package org.snomed.cdsservice.service.rules;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interprets a criterion's {@code code_selector} as SNOMED CT Expression Constraint Language (ECL) and
 * decides whether it can be resolved locally, without a terminology server.
 * <p>
 * The rule model uses ECL uniformly: a bare concept reference such as {@code 271062006 |Fasting blood
 * glucose measurement|} denotes exactly that concept, and a disjunction of bare concept references
 * ({@code A |..| OR B |..|}) denotes just that enumerated set. Both are resolved here in memory with no
 * network call. Any ECL that uses operators — descendants ({@code <}, {@code <<}), ancestors, refinements
 * ({@code :}), reference sets ({@code ^}), the wildcard ({@code *}), {@code AND}/{@code MINUS} — must be
 * expanded by the terminology server instead, and {@link Resolution#resolvableLocally()} is false.
 * <p>
 * This is a deliberately small recogniser, not a full ECL parser: it only detects and extracts the two
 * server-free shapes. Everything else is delegated to the server so the demonstrator can run without a
 * terminology server whenever its content sticks to enumerated concepts.
 */
public final class EclSelector {

	public static final String SNOMED_SYSTEM = "http://snomed.info/sct";

	// A single concept reference: a SCTID optionally followed by a |term|.
	private static final String CONCEPT = "\\d{6,18}(?:\\s*\\|[^|]*\\|)?";

	// The whole expression is one concept, or several joined only by OR (case-insensitive).
	private static final Pattern LITERAL_DISJUNCTION =
			Pattern.compile("\\s*(?:" + CONCEPT + ")(?:\\s+OR\\s+(?:" + CONCEPT + "))*\\s*", Pattern.CASE_INSENSITIVE);

	// Extracts each concept's SCTID, consuming any trailing |term| so digits inside a term are not matched.
	private static final Pattern CONCEPT_ID = Pattern.compile("(\\d{6,18})(?:\\s*\\|[^|]*\\|)?");

	private EclSelector() {
	}

	/**
	 * The outcome of inspecting an ECL selector.
	 *
	 * @param resolvableLocally true when the ECL is a bare concept or a disjunction of bare concepts
	 * @param codes             the concept codes when resolvable locally, otherwise empty
	 * @param ecl               the original ECL string (trimmed), for server expansion when needed
	 */
	public record Resolution(boolean resolvableLocally, Set<CodeKey> codes, String ecl) {
	}

	public static Resolution parse(String ecl) {
		if (ecl == null || ecl.isBlank()) {
			throw new IllegalArgumentException("ECL selector is empty.");
		}
		String trimmed = ecl.trim();
		if (!LITERAL_DISJUNCTION.matcher(trimmed).matches()) {
			return new Resolution(false, Set.of(), trimmed);
		}
		Set<CodeKey> codes = new LinkedHashSet<>();
		Matcher matcher = CONCEPT_ID.matcher(trimmed);
		while (matcher.find()) {
			codes.add(new CodeKey(SNOMED_SYSTEM, matcher.group(1)));
		}
		return new Resolution(true, codes, trimmed);
	}
}
