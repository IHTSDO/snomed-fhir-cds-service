package org.snomed.cdshooks.rest;

import org.snomed.cdshooks.rest.pojo.ServicesList;
import org.snomed.cdshooks.service.CDSServicesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "cds-services")
public class ServicesDiscoveryController {

	@Autowired
	private CDSServicesService cdsRuleService;

	@GetMapping
	@ResponseBody
	public ServicesList getServiceList() {
		return new ServicesList(cdsRuleService.loadAllServices());
	}

}
