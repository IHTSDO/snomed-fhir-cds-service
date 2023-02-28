package org.snomed.cdsservice.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.snomed.cdsservice.rest.pojo.CardsResponse;
import org.snomed.cdsservice.service.CDSServicesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping(path = "cds-services/{id}")
public class ServiceCallingController {

	@Autowired
	private CDSServicesService cdsRuleService;

	@Autowired
	private ObjectMapper mapper;

	@PostMapping
	@ResponseBody
	public CardsResponse callService(@PathVariable String id, @RequestBody CDSRequest request) throws IOException {
		request.populatePrefetchStrings(mapper);
		List<CDSCard> cards = cdsRuleService.call(id, request);
		return new CardsResponse(cards);
	}

}
