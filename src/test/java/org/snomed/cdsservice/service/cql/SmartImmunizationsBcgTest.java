package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.CommunicationRequest;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the official WHO SMART Immunizations decision table {@code IMMZ.D2.DT.BCG} through the embedded
 * {@link PlanDefinitionEngine} and checks every scenario of the IG's own test suite (patient bundle, evaluation
 * date, expected medication proposal and guidance text). No FHIR server or terminology server is involved.
 */
class SmartImmunizationsBcgTest {

	private static final Path CONTENT = Path.of("knowledge-bases/smart/immunizations-bcg");
	private static final Path TESTS = Path.of("src/test/resources/smart-immunizations/tests/IMMZD2DTBCG");

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();

	private static PlanDefinitionEngine engine;
	private static JsonNode expectations;

	@BeforeAll
	static void loadContent() throws IOException {
		engine = PlanDefinitionEngine.fromDirectory(FHIR_CONTEXT, CONTENT);
		expectations = new ObjectMapper().readTree(TESTS.resolve("expectations.json").toFile());
	}

	@TestFactory
	Stream<DynamicTest> officialScenarios() {
		String today = expectations.get("today").asText();
		List<DynamicTest> tests = new ArrayList<>();
		for (JsonNode testCase : expectations.get("cases")) {
			String patientId = testCase.get("patient").asText();
			tests.add(DynamicTest.dynamicTest(patientId + ": " + testCase.get("scenario").asText(), () -> {
				CarePlan carePlan = applyBcg(patientId, testCase.get("bundle").asText(), today);

				List<Resource> contained = carePlan.getContained();
				String guidance = contained.stream()
						.filter(CommunicationRequest.class::isInstance).map(CommunicationRequest.class::cast)
						.map(request -> request.getPayloadFirstRep().getContentStringType().getValue())
						.findFirst().orElse(null);
				boolean medicationProposed = contained.stream().anyMatch(MedicationRequest.class::isInstance);

				assertEquals(testCase.get("guidance").asText(), guidance, "guidance");
				assertEquals(testCase.get("medicationProposed").asBoolean(), medicationProposed, "medication proposal");
				assertEquals(testCase.get("containedCount").asInt(), contained.size(), () -> "contained resources: "
						+ contained.stream().map(r -> r.fhirType() + "/" + r.getIdPart()).toList());
			}));
		}
		return tests.stream();
	}

	@Test
	void warmEvaluationLatency() throws IOException {
		String today = expectations.get("today").asText();
		JsonNode cases = expectations.get("cases");
		// Warm up: compile libraries and expand value sets once.
		for (JsonNode testCase : cases) {
			applyBcg(testCase.get("patient").asText(), testCase.get("bundle").asText(), today);
		}

		int rounds = 5;
		List<Long> timings = new ArrayList<>();
		for (int round = 0; round < rounds; round++) {
			for (JsonNode testCase : cases) {
				long start = System.nanoTime();
				applyBcg(testCase.get("patient").asText(), testCase.get("bundle").asText(), today);
				timings.add((System.nanoTime() - start) / 1_000_000);
			}
		}
		timings.sort(Long::compare);
		long p50 = timings.get(timings.size() / 2);
		long p95 = timings.get((int) (timings.size() * 0.95) - 1);
		System.out.printf("IMMZ.D2.DT.BCG warm $apply over %d evaluations: p50=%d ms, p95=%d ms, max=%d ms%n",
				timings.size(), p50, p95, timings.get(timings.size() - 1));
		assertTrue(p95 < 300, "warm p95 should stay below 300 ms but was " + p95 + " ms");
	}

	private CarePlan applyBcg(String patientId, String bundleFile, String today) throws IOException {
		Bundle bundle = FHIR_CONTEXT.newJsonParser().parseResource(Bundle.class, Files.readString(TESTS.resolve(bundleFile)));
		List<Resource> patientData = bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList();

		Parameters parameters = new Parameters();
		parameters.addParameter().setName("Today").setValue(new DateType(today));

		CarePlan carePlan = (CarePlan) engine.apply("IMMZD2DTBCG", patientId, patientData, parameters);
		// The IG tests assert on the serialized $apply response. The in-memory CarePlan can hold the same request
		// resource twice (same id); serialization collapses contained resources by id, as a server response would.
		IParser parser = FHIR_CONTEXT.newJsonParser();
		return parser.parseResource(CarePlan.class, parser.encodeResourceToString(carePlan));
	}
}
