package org.snomed.cdsservice.testsupport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Coding;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.snomed.cdsservice.service.rules.EclCodeResolver;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.util.SnomedValueSetUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doReturn;

/**
 * Offline terminology server expansions for the operator-based ECL selectors in the diagnostic knowledge
 * bases. Diagnostic services expand these at startup, so tests stub them here instead of contacting a
 * terminology server.
 * <p>
 * The expansions live in {@value #RESOURCE}, which records the server and SNOMED CT release they were
 * taken from. Each {@code ecl} must match a {@code code_selector} in the TSV files exactly;
 * {@code RuleLoaderServiceTest} checks this so the fixture cannot silently drift from the content.
 */
public final class DiagnosticTerminologyFixture {

	public static final String RESOURCE = "/terminology-fixture/diagnostic-expansions.json";

	private static final String SNOMED = "http://snomed.info/sct";

	/** Expansions keyed by ECL, in the order they appear in the fixture file. */
	public static final Map<String, List<Coding>> EXPANSIONS = load();

	private static Map<String, List<Coding>> load() {
		try (InputStream in = DiagnosticTerminologyFixture.class.getResourceAsStream(RESOURCE)) {
			JsonNode root = new ObjectMapper().readTree(in);
			Map<String, List<Coding>> expansions = new LinkedHashMap<>();
			for (JsonNode expansion : root.get("expansions")) {
				List<Coding> codings = new ArrayList<>();
				for (JsonNode code : expansion.get("codes")) {
					codings.add(new Coding(SNOMED, code.get("code").asText(), code.get("display").asText()));
				}
				expansions.put(expansion.get("ecl").asText(), List.copyOf(codings));
			}
			return Collections.unmodifiableMap(expansions);
		} catch (IOException | NullPointerException e) {
			throw new IllegalStateException("Could not read terminology fixture " + RESOURCE, e);
		}
	}

	private DiagnosticTerminologyFixture() {
	}

	/** A Mockito mock client that answers the diagnostic expansions. */
	public static FHIRTerminologyServerClient mockClient() {
		return stub(Mockito.mock(FHIRTerminologyServerClient.class));
	}

	/** Stubs the diagnostic expansions on a Mockito mock. */
	public static FHIRTerminologyServerClient stub(FHIRTerminologyServerClient client) {
		EXPANSIONS.forEach((ecl, codings) ->
				doReturn(codings).when(client).expandValueSet(SnomedValueSetUtil.getSnomedECLValueSetURI(ecl)));
		return client;
	}

	/**
	 * A client that answers the diagnostic expansions and delegates every other call to {@code delegate},
	 * so tests keep stubbing (or calling) their own terminology client as before.
	 */
	public static FHIRTerminologyServerClient delegatingClient(FHIRTerminologyServerClient delegate) {
		return stub(Mockito.mock(FHIRTerminologyServerClient.class, AdditionalAnswers.delegatesTo(delegate)));
	}

	/**
	 * Import into a {@code @SpringBootTest} so diagnostic services can expand their ECL selectors while the
	 * context starts, without network access. The {@link EclCodeResolver} bean is rebuilt around
	 * {@link #delegatingClient}; other terminology calls still reach the test's own client.
	 */
	@TestConfiguration(proxyBeanMethods = false)
	public static class SpringConfig {

		@Bean
		static BeanPostProcessor diagnosticTerminologyStubber(ObjectProvider<FHIRTerminologyServerClient> clientProvider) {
			return new BeanPostProcessor() {
				@Override
				public Object postProcessBeforeInitialization(Object bean, String beanName) {
					if (bean instanceof EclCodeResolver) {
						return new EclCodeResolver(delegatingClient(clientProvider.getObject()));
					}
					return bean;
				}
			};
		}
	}
}
