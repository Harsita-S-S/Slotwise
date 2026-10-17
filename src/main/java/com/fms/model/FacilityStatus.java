package com.fms.model;

/**
 * Status of a facility in the system.
 * Defined in SRS Section 4.2 and FR-FAC-03:
 * - Available: Ready for booking and use.
 * - Under Maintenance: Temporarily closed for repairs/maintenance.
 * - Inactive: Deactivated by admin (not available for new bookings).
 */
public enum FacilityStatus {
    Available("Available"),
    Under_Maintenance("Under Maintenance"),
    Inactive("Inactive");

    private final String dbValue;

    FacilityStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return this.dbValue;
    }

    /**
     * Converts a database string value into the corresponding FacilityStatus enum.
     *
     * @param value The string from the database column
     * @return The matching FacilityStatus enum constant
     */
    public static FacilityStatus fromDbValue(String value) {
        if (value == null) {
            return Available;
        }

        if (value.equalsIgnoreCase("Available")) {
            return Available;
        } else if (value.equalsIgnoreCase("Under Maintenance")) {
            return Under_Maintenance;
        } else if (value.equalsIgnoreCase("Inactive")) {
            return Inactive;
        }

        return Available;
    }
}
