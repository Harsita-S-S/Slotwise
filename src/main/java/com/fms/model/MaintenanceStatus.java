package com.fms.model;

/**
 * Maintenance request status lifecycle defined in SRS BR-09 and FR-MNT-03.
 * Forward-only sequence: Reported -> Assigned -> In Progress -> Completed.
 */
public enum MaintenanceStatus {
    Reported,
    Assigned,
    InProgress,
    Completed
}
