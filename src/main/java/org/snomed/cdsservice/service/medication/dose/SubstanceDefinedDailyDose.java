package org.snomed.cdsservice.service.medication.dose;

import java.math.BigDecimal;

public record SubstanceDefinedDailyDose(String atcRouteOfAdministration, float dose, String unit, String atcCode) {

}
