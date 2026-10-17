package com.fms.model;

/**
 * Visual status of a specific 1-hour time slot on the interactive grid (FR-BKG-02).
 */
public enum SlotStatus {
    FREE("Free"),
    BOOKED("Booked"),
    PENDING("Pending Approval"),
    MAINTENANCE("Maintenance"),
    PAST("Past");

    private final String displayName;

    SlotStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
