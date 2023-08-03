package org.snomed.cdsservice.model;

import java.util.List;

public class CDSReference {
    List<CDSCoding> coding;

    public CDSReference(List<CDSCoding> coding) {
        this.coding = coding;
    }

    public List<CDSCoding> getCoding() {
        return coding;
    }

    public void setCoding(List<CDSCoding> coding) {
        this.coding = coding;
    }
}
