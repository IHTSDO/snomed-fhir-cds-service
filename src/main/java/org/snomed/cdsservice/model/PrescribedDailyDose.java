package org.snomed.cdsservice.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PrescribedDailyDose {
    BigDecimal quantity;
    String unit;

    public PrescribedDailyDose(BigDecimal quantity, String unit) {
        this.quantity = quantity.setScale(5, RoundingMode.HALF_UP);
        this.unit = unit;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public void addQuantity(BigDecimal newQuantity) {
        this.quantity = this.quantity.add(newQuantity);
    }

}
