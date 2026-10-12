package org.snomed.cdsservice.service.cql;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Library;
import org.hl7.fhir.r4.model.ValueSet;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.service.ServiceException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EclValueSetPreloaderTest {

	private static final String SNOMED = "http://snomed.info/sct";

	@Test
	void singleEclFilterIsUsedVerbatim() throws ServiceException {
		assertEquals("<< 28442001 |Polyuria|", EclValueSetPreloader.eclOf(eclValueSet("<< 28442001 |Polyuria|")));
	}

	@Test
	void filtersAndEnumeratedConceptsAreCombinedWithOr() throws ServiceException {
		ValueSet valueSet = eclValueSet("<< 28442001");
		valueSet.getCompose().getIncludeFirstRep().addConcept().setCode("17173007");
		valueSet.getCompose().addInclude().setSystem(SNOMED).addFilter()
				.setProperty("constraint").setOp(ValueSet.FilterOperator.EQUAL).setValue("<< 84229001");
		assertEquals("(<< 28442001) OR (17173007) OR (<< 84229001)", EclValueSetPreloader.eclOf(valueSet));
	}

	@Test
	void valueSetsWithoutEclAreLeftToTheEngine() throws ServiceException {
		ValueSet enumerated = new ValueSet();
		enumerated.getCompose().addInclude().setSystem(SNOMED).addConcept().setCode("28442001");
		assertNull(EclValueSetPreloader.eclOf(enumerated));
	}

	@Test
	void unsupportedComposeFailsFast() {
		ValueSet withExclude = eclValueSet("<< 28442001");
		withExclude.getCompose().addExclude().setSystem(SNOMED).addConcept().setCode("28442001");
		assertThrows(ServiceException.class, () -> EclValueSetPreloader.eclOf(withExclude));

		ValueSet mixedSystems = eclValueSet("<< 28442001");
		mixedSystems.getCompose().addInclude().setSystem("http://loinc.org").addConcept().setCode("1558-6");
		assertThrows(ServiceException.class, () -> EclValueSetPreloader.eclOf(mixedSystems));
	}

	@Test
	void findsEclValueSetResourcesAndImplicitEclUrlsInCql() throws ServiceException {
		ValueSet valueSet = eclValueSet("<< 28442001");
		valueSet.setUrl("http://example.org/ValueSet/polyuria").setVersion("1.0.0");

		Library library = new Library();
		library.addContent().setContentType("text/cql").setData("""
				valueset "Crisis": 'http://snomed.info/sct?fhir_vs=ecl/<< 420422005 |Diabetic ketoacidosis|'
				valueset "Encoded": 'http://snomed.info/sct?fhir_vs=ecl/%3C%3C%2044054006'
				valueset "Other": 'http://example.org/ValueSet/other'
				""".getBytes(StandardCharsets.UTF_8));

		Map<String, String> found = EclValueSetPreloader.findEclValueSets(List.<IBaseResource>of(valueSet, library));
		assertEquals(Map.of(
				"http://example.org/ValueSet/polyuria", "<< 28442001",
				"http://example.org/ValueSet/polyuria|1.0.0", "<< 28442001",
				"http://snomed.info/sct?fhir_vs=ecl/<< 420422005 |Diabetic ketoacidosis|", "<< 420422005 |Diabetic ketoacidosis|",
				"http://snomed.info/sct?fhir_vs=ecl/%3C%3C%2044054006", "<< 44054006"), found);
	}

	private static ValueSet eclValueSet(String ecl) {
		ValueSet valueSet = new ValueSet();
		valueSet.getCompose().addInclude().setSystem(SNOMED).addFilter()
				.setProperty("constraint").setOp(ValueSet.FilterOperator.EQUAL).setValue(ecl);
		return valueSet;
	}
}
