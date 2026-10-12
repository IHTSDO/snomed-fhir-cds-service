package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
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
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the WHO SMART Immunizations decision tables of the Jamaica schedule through the embedded
 * {@link PlanDefinitionEngine} and checks every scenario of the IG's own test suite: patient bundle, evaluation date,
 * guidance text, number of vaccine proposals, and number of resources in the response. No FHIR server or terminology
 * server is involved.
 * <p>
 * Requests built from the same ActivityDefinition can share an id (e.g. the bOPV and IPV proposals of the polio
 * table), so resources are counted after removing only content-identical duplicates, as the card mapper does.
 */
class SmartImmunizationsOfficialScenariosTest {

	private static final Path CONTENT = Path.of("knowledge-bases/smart/immunizations-jm");
	private static final Path TESTS = Path.of("src/test/resources/smart-immunizations/tests");

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();

	/**
	 * Scenarios where the IG's tests expect one vaccine proposal but the guidance asks for two (a tetanus-diphtheria
	 * booster and a pertussis booster). Both proposals come from the same ActivityDefinition and get the same id, so
	 * on the HAPI server used by the IG's tests one is lost when the response is serialized; the expected count
	 * captured that. This service keeps both, matching the guidance.
	 */
	private static final Set<String> TWO_BOOSTERS_PROPOSED = Set.of("DTP15.3", "DTP17.3", "DTP23.4", "DTP39.3", "DTP41.3", "DTP44.4");

	private static PlanDefinitionEngine engine;

	@BeforeAll
	static void loadContent() throws IOException {
		engine = PlanDefinitionEngine.fromDirectory(FHIR_CONTEXT, CONTENT);
	}

	@TestFactory
	Stream<DynamicContainer> officialScenarios() throws IOException {
		List<DynamicContainer> tables = new ArrayList<>();
		try (Stream<Path> directories = Files.list(TESTS)) {
			for (Path directory : directories.filter(Files::isDirectory).sorted().toList()) {
				JsonNode expectations = new ObjectMapper().readTree(directory.resolve("expectations.json").toFile());
				String planDefinitionId = expectations.get("planDefinition").asText();
				String today = expectations.get("today").asText();
				List<DynamicTest> scenarios = new ArrayList<>();
				for (JsonNode testCase : expectations.get("cases")) {
					String patientId = testCase.get("patient").asText();
					scenarios.add(DynamicTest.dynamicTest(patientId + ": " + testCase.get("scenario").asText(), () -> {
						List<Resource> contained = CarePlanCardMapper.distinctById(
								apply(planDefinitionId, directory, patientId, testCase.get("bundle").asText(), today).getContained());
						String guidance = contained.stream()
								.filter(CommunicationRequest.class::isInstance).map(CommunicationRequest.class::cast)
								.map(request -> request.getPayloadFirstRep().getContentStringType().getValue())
								.findFirst().orElse(null);
						long proposals = contained.stream().filter(MedicationRequest.class::isInstance).count();

						int extra = TWO_BOOSTERS_PROPOSED.contains(patientId) ? 1 : 0;
						assertEquals(testCase.get("guidance").asText(), guidance, "guidance");
						assertEquals(testCase.get("proposalCount").asInt() + extra, proposals, "vaccine proposals");
						assertEquals(testCase.get("containedCount").asInt() + extra, contained.size(), () -> "contained resources: "
								+ contained.stream().map(r -> r.fhirType() + "/" + r.getIdPart()).toList());
					}));
				}
				tables.add(DynamicContainer.dynamicContainer(planDefinitionId, scenarios));
			}
		}
		return tables.stream();
	}

	@Test
	void warmEvaluationLatency() throws IOException {
		Path directory = TESTS.resolve("IMMZD2DTBCG");
		JsonNode expectations = new ObjectMapper().readTree(directory.resolve("expectations.json").toFile());
		String today = expectations.get("today").asText();
		JsonNode cases = expectations.get("cases");
		// Warm up: compile libraries and expand value sets once.
		for (JsonNode testCase : cases) {
			apply("IMMZD2DTBCG", directory, testCase.get("patient").asText(), testCase.get("bundle").asText(), today);
		}

		int rounds = 5;
		List<Long> timings = new ArrayList<>();
		for (int round = 0; round < rounds; round++) {
			for (JsonNode testCase : cases) {
				long start = System.nanoTime();
				apply("IMMZD2DTBCG", directory, testCase.get("patient").asText(), testCase.get("bundle").asText(), today);
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

	private CarePlan apply(String planDefinitionId, Path directory, String patientId, String bundleFile, String today) throws IOException {
		Bundle bundle = FHIR_CONTEXT.newJsonParser().parseResource(Bundle.class, Files.readString(directory.resolve(bundleFile)));
		List<Resource> patientData = bundle.getEntry().stream().map(Bundle.BundleEntryComponent::getResource).toList();

		Parameters parameters = new Parameters();
		parameters.addParameter().setName("Today").setValue(new DateType(today));

		return (CarePlan) engine.apply(planDefinitionId, patientId, patientData, parameters);
	}
}
