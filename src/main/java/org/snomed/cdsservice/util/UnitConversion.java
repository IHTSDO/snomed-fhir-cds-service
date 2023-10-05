package org.snomed.cdsservice.util;

import java.util.HashMap;
import java.util.Map;

public enum UnitConversion {
    MG_MG("mg", "mg", 1.0),
    MG_G("mg", "g", 0.001),
    MG_MCG("mg", "mcg", 1000.0),
    G_MG("g", "mg", 1000.0),
    G_G("g", "g", 1.0),
    G_MCG("g", "mcg", 1000000.0),
    MCG_MG("mcg", "mg", 0.001),
    MCG_G("mcg", "g", 0.000001),
    MCG_MCG("mcg", "mcg", 1.0),
    L_L("L", "L", 1.0),
    L_ML("L", "mL", 1000.0),
    L_UL("L", "uL", 1000000.0),
    ML_L("mL", "L", 0.001),
    ML_ML("mL", "mL", 1.0),
    ML_UL("mL", "uL", 1000.0),
    UL_L("uL", "L", 0.000001),
    UL_ML("uL", "mL", 0.001),
    UL_UL("uL", "uL", 1.0),
    TABLET_TABLET("Tablet", "Tablet", 1.0),
    TABLET_CAPSULE("Tablet", "Capsule", 1.0),
    CAPSULE_CAPSULE("Capsule", "Capsule", 1.0);


    private static final Map<String, Double> BY_UNIT = new HashMap<>();

    static {
        for (UnitConversion e : values()) {
            BY_UNIT.put(e.inputUnit + "-" + e.targetUnit, e.getFactor());
        }
    }

    public final String inputUnit;
    public final String targetUnit;
    public final Double factor;

    private UnitConversion(String inputUnit, String targetUnit, Double factor) {
        this.inputUnit = inputUnit;
        this.targetUnit = targetUnit;
        this.factor = factor;
    }

    public static Double factorOfConversion(String sourceUnit, String targetUnit) {
        return BY_UNIT.get(sourceUnit+"-"+targetUnit);
    }

    //getter
    public String getInputUnit() {
        return inputUnit;
    }

    public String getTargetUnit() {
        return targetUnit;
    }

    public Double getFactor() {
        return factor;
    }
}

