package com.fms.model;

/**
 * Notification event types (FR-NOT-01 to FR-NOT-03). The enum name is stored in notifications.type.
 * Each type carries a display title and icon for the notification panel.
 */
public enum NotificationType {
    BOOKING_PENDING("Booking awaiting approval", "\uD83D\uDCC5"),
    BOOKING_APPROVED("Booking approved", "\u2714"),
    BOOKING_REJECTED("Booking rejected", "\u2716"),
    BOOKING_CANCELLED("Booking cancelled", "\u2716"),
    BOOKING_COMPLETED("Booking completed", "\u2714"),
    BOOKING_REMINDER("Upcoming booking", "\u23F0"),
    BOOKING_AFFECTED("Booking affected by maintenance", "\u26A0"),
    MAINTENANCE_ASSIGNED("Maintenance assigned", "\uD83D\uDD27"),
    MAINTENANCE_COMPLETED("Maintenance completed", "\uD83D\uDD27"),
    MAINTENANCE_URGENT("Urgent maintenance", "\u26A0"),
    MAINTENANCE_DUE("Maintenance due today", "\u23F0"),
    MAINTENANCE_OVERDUE("Maintenance overdue", "\u26A0"),
    FACILITY_DEACTIVATED("Facility deactivated", "\u26A0"),
    GENERAL("Notification", "\u2139");

    private final String title;
    private final String icon;

    NotificationType(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public String getIcon() {
        return icon;
    }

    /** Maps a stored type string to its constant; unknown or null values map to GENERAL. */
    public static NotificationType fromString(String value) {
        if (value != null) {
            for (NotificationType t : values()) {
                if (t.name().equalsIgnoreCase(value.trim())) {
                    return t;
                }
            }
        }
        return GENERAL;
    }
}
