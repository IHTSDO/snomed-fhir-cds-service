package org.snomed.cdsservice.util;

;

public class Frequency {
    public final int frequencyCount;
    public final int periodCount;
    public final String periodUnit;


    public Frequency( int frequencyCount, int periodCount, String periodUnit) {
        this.frequencyCount = frequencyCount;
        this.periodCount = periodCount;
        this.periodUnit = periodUnit;
    }

    public int getFrequencyCount() {
        return frequencyCount;
    }

    public int getPeriodCount() {
        return periodCount;
    }

    public String getPeriodUnit() {
        return periodUnit;
    }
}
