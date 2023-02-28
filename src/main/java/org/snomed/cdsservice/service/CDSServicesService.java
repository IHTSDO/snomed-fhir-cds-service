package org.snomed.cdsservice.service;

import jakarta.annotation.PostConstruct;
import org.snomed.cdsservice.model.CDSCard;
import org.snomed.cdsservice.rest.pojo.CDSRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

@Service
public class CDSServicesService {

	@Autowired
	private ApplicationContext applicationContext;

	private final List<CDSService> services = new ArrayList<>();

	@PostConstruct
	public void init() {
		services.addAll(applicationContext.getBeansOfType(CDSService.class).values());
	}

	public List<CDSService> loadAllServices() {
		return services;
	}

	public List<CDSCard> call(String serviceId, CDSRequest cdsRequest) {
		CDSService service = loadAllServices().stream().filter(aService -> aService.getId().equals(serviceId)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(404, "A service with this id was not found.", null));
		return service.call(cdsRequest);
	}
}
