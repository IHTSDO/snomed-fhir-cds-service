package org.snomed.cdsservice.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class AggregatedMedicationsBySubstance {
    String substanceShortName;
    List<String> medicationsList;

    Map<String, DosageComparisonByRoute> dosageComparisonByRouteMap = new HashMap<>();
    List<CDSReference> referenceList;

    public AggregatedMedicationsBySubstance(String substanceShortName, List<String> medicationsList, List<CDSReference> referenceList) {
        this.substanceShortName = substanceShortName;
        this.medicationsList = medicationsList;
        this.referenceList = referenceList;
    }

    public String getSubstanceShortName() {
        return substanceShortName;
    }

    public void setSubstanceShortName(String substanceShortName) {
        this.substanceShortName = substanceShortName;
    }

    public List<String> getMedicationsList() {
        return medicationsList;
    }

    public void setMedicationsList(List<String> medicationsList) {
        this.medicationsList = medicationsList;
    }

    public List<CDSReference> getReferenceList() {
        return referenceList;
    }

    public void setReferenceList(List<CDSReference> referenceList) {
        this.referenceList = referenceList;
    }

    public Map<String, DosageComparisonByRoute> getDosageComparisonByRouteMap() {
        return dosageComparisonByRouteMap;
    }

    public void setDosageComparisonByRouteMap(Map<String, DosageComparisonByRoute> dosageComparisonByRouteMap) {
        this.dosageComparisonByRouteMap = dosageComparisonByRouteMap;
    }
}
