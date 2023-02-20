package org.snomed.cdshooks.rest.pojo;

import org.snomed.cdshooks.model.CDSService;

import java.util.List;

public class ServicesList {

	private final List<CDSService> services;

	public ServicesList(List<CDSService> services) {
		this.services = services;
	}

	public List<CDSService> getServices() {
		return services;
	}
}
