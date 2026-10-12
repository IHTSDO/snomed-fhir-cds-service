package org.snomed.cdsservice.service.cql;

import ca.uhn.fhir.context.FhirContext;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.CommunicationRequest;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.snomed.cdsservice.service.ServiceException;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredExpansionsTest {

	private static final FhirContext FHIR_CONTEXT = FhirContext.forR4Cached();
	private static final String SNOMED = "http://snomed.info/sct";
	private static final String VERSION = "http://snomed.info/sct/900000000000207008/version/20260701";

	@TempDir
	Path contentDirectory;

	@Test
	void savedExpansionsLoadBackWithTheirVersion() throws Exception {
		String implicitUrl = EclValueSetPreloader.IMPLICIT_ECL_PREFIX + "<< 420422005 |Diabetic ketoacidosis|";
		StoredExpansions stored = StoredExpansions.load(FHIR_CONTEXT, contentDirectory);
		stored.save(implicitUrl, "<< 420422005 |Diabetic ketoacidosis|",
				List.of(new Coding(SNOMED, "420422005", "Diabetic ketoacidosis"), new Coding(SNOMED, "111556005", null)), VERSION);
		stored.save("http://example.org/ValueSet/polyuria|1.0.0", "<< 28442001", List.of(new Coding(SNOMED, "28442001", "Polyuria")), VERSION);

		StoredExpansions reloaded = StoredExpansions.load(FHIR_CONTEXT, contentDirectory);
		StoredExpansions.Expansion crisis = reloaded.find(implicitUrl, "<< 420422005 |Diabetic ketoacidosis|").orElseThrow();
		assertEquals(VERSION, crisis.codeSystemVersion());
		assertEquals(List.of("111556005", "420422005"), crisis.codings().stream().map(Coding::getCode).toList(), "sorted by code");
		assertEquals("Diabetic ketoacidosis", crisis.codings().get(1).getDisplay());

		assertTrue(reloaded.find("http://example.org/ValueSet/polyuria|1.0.0", "<< 28442001").isPresent());
		assertTrue(reloaded.find("http://example.org/ValueSet/polyuria", "<< 28442001").isPresent());
		assertEquals(Optional.empty(), reloaded.find("http://example.org/ValueSet/polyuria|2.0.0", "<< 28442001"));
	}

	@Test
	void anEclChangedWithoutRefreshingItsExpansionFailsLoading() throws Exception {
		StoredExpansions stored = StoredExpansions.load(FHIR_CONTEXT, contentDirectory);
		stored.save("http://example.org/ValueSet/polyuria", "<< 28442001", List.of(new Coding(SNOMED, "28442001", null)), VERSION);

		ServiceException e = assertThrows(ServiceException.class,
				() -> StoredExpansions.load(FHIR_CONTEXT, contentDirectory).find("http://example.org/ValueSet/polyuria", "<< 28442001 OR << 17173007"));
		assertTrue(e.getMessage().contains("different ECL"), e.getMessage());
	}

	@Test
	void diabetesContentLoadsAndRunsWithoutATerminologyServer() throws Exception {
		Path content = Path.of("knowledge-bases/smart/diabetes-jm");
		PlanDefinitionEngine engine = PlanDefinitionEngine.fromDirectory(FHIR_CONTEXT, content);
		EclValueSetPreloader noTerminologyServer = new EclValueSetPreloader(ecl -> {
			throw new ServiceException("No terminology server in this test (ECL '" + ecl + "').");
		});

		noTerminologyServer.preload(engine, StoredExpansions.load(FHIR_CONTEXT, content), false);

		assertEquals(List.of(VERSION), List.copyOf(engine.getTerminologyVersions()));
		List<Resource> data = new ArrayList<>();
		data.add(new Patient().setId("p1"));
		data.add(ogtt("o1", "12.0", "2026-09-01"));
		data.add(ogtt("o2", "11.5", "2026-09-08"));
		CarePlan carePlan = (CarePlan) engine.apply("DM2024OGTT01", "p1", data, null);
		assertTrue(carePlan.getContained().stream().anyMatch(CommunicationRequest.class::isInstance), "card produced");
	}

	private static Observation ogtt(String id, String value, String date) {
		Observation observation = new Observation();
		observation.setId(id);
		observation.setStatus(Observation.ObservationStatus.FINAL);
		observation.setSubject(new Reference("Patient/p1"));
		observation.getCode().addCoding().setSystem(SNOMED).setCode("113076002");
		observation.getCode().addCoding().setSystem(SNOMED).setCode("123030002");
		observation.setEffective(new DateTimeType(date));
		observation.setValue(new Quantity().setValue(new BigDecimal(value)).setSystem("http://unitsofmeasure.org").setCode("mmol/L"));
		return observation;
	}
}
