package org.snomed.cdsservice.service;

import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HelloCDSService extends CDSService {

	public HelloCDSService() {
		super("hello-test");
	}

	@Override
	public List<CDSCard> call(CDSRequest cdsRequest) {
		CDSCard card = new CDSCard("Hello! The CDS Service is working.", CDSIndicator.info, new CDSSource("http://example.com"));
		card.setDetail("This is an example card from the 'hello-test' CDS Service.");
		return List.of(card);
	}

}
