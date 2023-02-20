package org.snomed.cdshooks.service;

import org.snomed.cdshooks.model.CDSCard;
import org.snomed.cdshooks.model.CDSService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

@Service
public class CDSServicesService {

	private final List<CDSService> services = List.of(new HelloCDSService());

	public List<CDSService> loadAllServices() {
		return services;
	}

	public List<CDSCard> call(String serviceId) {
		CDSService service = loadAllServices().stream().filter(aService -> aService.getId().equals(serviceId)).findFirst()
				.orElseThrow(() -> new ResponseStatusException(404, "A service with this id was not found.", null));
		return service.call();
	}
}
