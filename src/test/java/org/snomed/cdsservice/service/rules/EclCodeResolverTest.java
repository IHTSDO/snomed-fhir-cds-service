package org.snomed.cdsservice.service.rules;

import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.api.Test;
import org.snomed.cdsservice.service.ServiceException;
import org.snomed.cdsservice.service.tsclient.FHIRTerminologyServerClient;
import org.snomed.cdsservice.util.SnomedValueSetUtil;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EclCodeResolverTest {

	private static final String SNOMED = "http://snomed.info/sct";
	private static final String VERSION = "http://snomed.info/sct/900000000000207008/version/20260701";

	private final FHIRTerminologyServerClient client = mock(FHIRTerminologyServerClient.class);
	private final EclCodeResolver resolver = new EclCodeResolver(client);

	@Test
	void expandRecordsTheSnomedVersionOfTheServerExpansion() throws ServiceException {
		stub("<< 44054006", codes(14), VERSION);

		EclCodeResolver.EclExpansion expansion = resolver.expand("<< 44054006");

		assertEquals(14, expansion.codes().size());
		assertEquals(VERSION, expansion.codeSystemVersion());
		assertEquals(Optional.of(VERSION), resolver.expansionVersion("<< 44054006"));
	}

	@Test
	void locallyResolvableEclNeedsNoServerAndHasNoVersion() throws ServiceException {
		EclCodeResolver.EclExpansion expansion = resolver.expand("28442001 |Polyuria| OR 17173007");

		assertEquals(2, expansion.codes().size());
		assertNull(expansion.codeSystemVersion());
		verifyNoInteractions(client);
	}

	@Test
	void expansionsAboveTheLimitAreRejected() {
		resolver.setMaxExpansionSize(100);
		stub("<< 363346000", codes(101), VERSION);

		ServiceException e = assertThrows(ServiceException.class, () -> resolver.expand("<< 363346000"));
		assertTrue(e.getMessage().contains("101 concepts, above the limit of 100"), e.getMessage());
		// Optional refinements fall back instead of failing.
		assertEquals(Optional.empty(), resolver.tryExpand("<< 363346000"));
	}

	@Test
	void emptyExpansionsAreRejected() {
		stub("<< 999999999", List.of(), VERSION);
		assertThrows(ServiceException.class, () -> resolver.expand("<< 999999999"));
	}

	private void stub(String ecl, List<Coding> codings, String version) {
		String uri = SnomedValueSetUtil.getSnomedECLValueSetURI(ecl);
		doReturn(codings).when(client).expandValueSet(uri);
		doReturn(Optional.ofNullable(version)).when(client).getExpansionVersion(uri);
	}

	private static List<Coding> codes(int count) {
		return IntStream.range(0, count).mapToObj(i -> new Coding(SNOMED, String.valueOf(100000 + i), null)).toList();
	}
}
