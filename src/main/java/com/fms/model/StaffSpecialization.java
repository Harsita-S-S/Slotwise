package com.fms.model;

/**
 * Staff trade specializations defined in SRS Section 4.4 and FR-STF-02.
 * Includes at least: Electrician, Lab Technician, Plumber, IT Support, General.
 */
public enum StaffSpecialization {
    Electrician("Electrician"),
    LabTechnician("Lab Technician"),
    Plumber("Plumber"),
    ITSupport("IT Support"),
    General("General");

    private final String displayName;

    StaffSpecialization(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Converts a database or string value into the corresponding enum constant.
     *
     * @param value String value
     * @return StaffSpecialization enum
     */
    public static StaffSpecialization fromString(String value) {
        if (value == null) {
            return General;
        }

        String cleaned = value.trim();
        if (cleaned.equalsIgnoreCase("Electrician")) {
            return Electrician;
        } else if (cleaned.equalsIgnoreCase("Lab Technician") || cleaned.equalsIgnoreCase("LabTechnician")) {
            return LabTechnician;
        } else if (cleaned.equalsIgnoreCase("Plumber")) {
            return Plumber;
        } else if (cleaned.equalsIgnoreCase("IT Support") || cleaned.equalsIgnoreCase("ITSupport")) {
            return ITSupport;
        } else if (cleaned.equalsIgnoreCase("General")) {
            return General;
        }

        return General;
    }
}
