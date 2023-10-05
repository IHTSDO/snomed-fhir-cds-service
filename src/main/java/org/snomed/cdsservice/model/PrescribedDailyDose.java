package org.snomed.cdsservice.model;

public class PrescribedDailyDose {
    Double quantity;
    String unit;

    public PrescribedDailyDose(Double quantity, String unit) {
        this.quantity = quantity;
        this.unit = unit;
    }

    public Double getQuantity() {
        return quantity;
    }

    public void setQuantity(Double quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void addQuantity(Double newQuantity) {
        this.quantity += newQuantity;
    }

}
