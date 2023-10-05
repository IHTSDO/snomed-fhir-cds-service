package org.snomed.cdsservice.model;

import org.snomed.cdsservice.service.model.SubstanceDefinedDailyDose;

public class DosageComparisonByRoute {
    PrescribedDailyDose totalPrescribedDailyDose;
    SubstanceDefinedDailyDose substanceDefinedDailyDose;
    String routeOfAdministration;

    public DosageComparisonByRoute(PrescribedDailyDose totalPrescribedDailyDose, SubstanceDefinedDailyDose substanceDefinedDailyDose, String routeOfAdministration) {
        this.totalPrescribedDailyDose = totalPrescribedDailyDose;
        this.substanceDefinedDailyDose = substanceDefinedDailyDose;
        this.routeOfAdministration = routeOfAdministration;
    }

    public PrescribedDailyDose getTotalPrescribedDailyDose() {
        return totalPrescribedDailyDose;
    }

    public void setTotalPrescribedDailyDose(PrescribedDailyDose totalPrescribedDailyDose) {
        this.totalPrescribedDailyDose = totalPrescribedDailyDose;
    }

    public SubstanceDefinedDailyDose getSubstanceDefinedDailyDose() {
        return substanceDefinedDailyDose;
    }

    public void setSubstanceDefinedDailyDose(SubstanceDefinedDailyDose substanceDefinedDailyDose) {
        this.substanceDefinedDailyDose = substanceDefinedDailyDose;
    }

    public String getRouteOfAdministration() {
        return routeOfAdministration;
    }

    public void setRouteOfAdministration(String routeOfAdministration) {
        this.routeOfAdministration = routeOfAdministration;
    }
}
