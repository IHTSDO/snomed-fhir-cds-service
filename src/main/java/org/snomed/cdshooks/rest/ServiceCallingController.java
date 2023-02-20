package org.snomed.cdshooks.rest;

import org.snomed.cdshooks.rest.pojo.CardsResponse;
import org.snomed.cdshooks.service.CDSServicesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "cds-services/{id}")
public class ServiceCallingController {

	@Autowired
	private CDSServicesService cdsRuleService;

	@GetMapping
	@ResponseBody
	public CardsResponse callService(@PathVariable String id) {
		return new CardsResponse(cdsRuleService.call(id));
	}

}
