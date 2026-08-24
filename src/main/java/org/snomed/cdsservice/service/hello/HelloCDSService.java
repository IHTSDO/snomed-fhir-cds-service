package org.snomed.cdsservice.service.hello;

import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.model.CDSIndicator;
import org.snomed.cdsservice.model.CDSSource;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.snomed.cdsservice.service.CDSService;

import java.util.List;

public class HelloCDSService extends CDSService {
	private static final String CONTRAINDICATION_ALERT_TYPE = "Contraindication";


	public HelloCDSService() {
		super("hello-test");
		setHook("patient-view");
		setTitle("Hello Test");
		setDescription("Returns a simple greeting card for connectivity testing.");
		setUsageRequirements("Intended as a lightweight smoke test for CDS client integration.");
	}

	@Override
	public List<CDSCard> call(CDSRequest cdsRequest) {
		CDSCard card = new CDSCard("22e982b7-4786-4afe-9f67-5af8266363f6",
				"Hello! The CDS Service is working.", null, CDSIndicator.info, new CDSSource("http://example.com"), null, null, CONTRAINDICATION_ALERT_TYPE);
		card.setDetail("This is an example card from the 'hello-test' CDS Service.");
		return List.of(card);
	}

}
