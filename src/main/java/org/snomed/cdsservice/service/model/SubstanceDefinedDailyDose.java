package org.snomed.cdsservice.service.model;

public class SubstanceDefinedDailyDose {

	private final String atcRouteOfAdministration;
	private final float dose;
	private final String unit;
	private final String atcCode;


	public SubstanceDefinedDailyDose(String atcRouteOfAdministration, float dose, String unit, String atcCode) {
		this.dose = dose;
		this.unit = unit;
		this.atcRouteOfAdministration = atcRouteOfAdministration;
		this.atcCode = atcCode;
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
	public String getAtcCode() {
		return atcCode;
	}
}
