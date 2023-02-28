package org.snomed.cdsservice.rest.pojo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CDSRequestTest {

	private ObjectMapper mapper;

	@BeforeEach
	public void setup() {
		mapper = new ObjectMapper();
	}

	@Test
	public void test() throws JsonProcessingException {
		CDSRequest cdsRequest = mapper.readValue("{\"prefetch\": {\"objectA\": { \"propertyA\": 123 }}}", CDSRequest.class);
		cdsRequest.populatePrefetchStrings(mapper);

		Map<String, String> prefetch = cdsRequest.getPrefetchStrings();
		assertNotNull(prefetch);
		assertEquals("{\"propertyA\":123}", prefetch.get("objectA"));
	}

}
