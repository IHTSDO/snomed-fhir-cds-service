package org.snomed.cdsservice.service.model;

import java.util.Set;

public record ManyToOneMapEntry(Set<String> sourceCodes, String targetCode, int mapPriority, String label) {

}
