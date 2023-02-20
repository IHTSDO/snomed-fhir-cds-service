package org.snomed.cdshooks.service;

import org.snomed.cdshooks.model.CDSCard;
import org.snomed.cdshooks.model.CDSIndicator;
import org.snomed.cdshooks.model.CDSService;
import org.snomed.cdshooks.model.CDSSource;

import java.util.List;

public class HelloCDSService extends CDSService {

	public HelloCDSService() {
		super("hello-test");
	}

	@Override
	public List<CDSCard> call() {
		CDSCard card = new CDSCard("Hello! The CDS Service is working.", CDSIndicator.info, new CDSSource("http://example.com"));
		card.setDetail("This is an example card from the 'hello-test' CDS Service.");
		return List.of(card);
	}
}
