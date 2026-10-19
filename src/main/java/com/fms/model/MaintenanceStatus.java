package com.fms.model;

/**
 * Maintenance request status lifecycle defined in SRS BR-09 and FR-MNT-03.
 * Forward-only sequence: Reported -> Assigned -> In Progress -> Completed.
 */
public enum MaintenanceStatus {
    Reported("Reported"),
    Assigned("Assigned"),
    InProgress("In Progress"),
    Completed("Completed");

    private final String dbValue;

    MaintenanceStatus(String dbValue) {
        this.dbValue = dbValue;
    }

    /** Value stored in the maintenance_requests.status ENUM column. */
    public String getDbValue() {
        return dbValue;
    }

    public static MaintenanceStatus fromDbValue(String value) {
        if (value != null) {
            String cleaned = value.trim();
            for (MaintenanceStatus s : values()) {
                if (s.dbValue.equalsIgnoreCase(cleaned) || s.name().equalsIgnoreCase(cleaned)) {
                    return s;
                }
            }
        }
        return Reported;
    }
}
