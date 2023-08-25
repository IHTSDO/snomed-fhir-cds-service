package org.snomed.cdsservice.service.model;

public class SubstanceDefinedDailyDose {

	private final String atcRouteOfAdministration;
	private final float dose;
	private final String unit;

	public SubstanceDefinedDailyDose(String atcRouteOfAdministration, float dose, String unit) {
		this.dose = dose;
		this.unit = unit;
		this.atcRouteOfAdministration = atcRouteOfAdministration;
	}

	public String getAtcRouteOfAdministration() {
		return atcRouteOfAdministration;
	}

	public float getDose() {
		return dose;
	}

	public String getUnit() {
		return unit;
	}
}
