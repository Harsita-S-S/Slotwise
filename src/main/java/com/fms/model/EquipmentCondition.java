package com.fms.model;

/**
 * Equipment conditions defined in SRS FR-EQP-02.
 * Tracks equipment lifecycle: Good, Needs Repair, Under Repair, Retired.
 */
public enum EquipmentCondition {
    Good("Good"),
    NeedsRepair("Needs Repair"),
    UnderRepair("Under Repair"),
    Retired("Retired");

    private final String dbValue;

    EquipmentCondition(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return this.dbValue;
    }

    /**
     * Converts a database or UI string into the corresponding EquipmentCondition enum.
     *
     * @param value The string representation
     * @return The matching EquipmentCondition enum constant
     */
    public static EquipmentCondition fromDbValue(String value) {
        if (value == null) {
            return Good;
        }

        String cleaned = value.trim();
        if (cleaned.equalsIgnoreCase("Good")) {
            return Good;
        } else if (cleaned.equalsIgnoreCase("Needs Repair") || cleaned.equalsIgnoreCase("NeedsRepair")) {
            return NeedsRepair;
        } else if (cleaned.equalsIgnoreCase("Under Repair") || cleaned.equalsIgnoreCase("UnderRepair")) {
            return UnderRepair;
        } else if (cleaned.equalsIgnoreCase("Retired")) {
            return Retired;
        }

        return Good;
    }
}
