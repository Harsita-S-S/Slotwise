package com.fms.model;

/**
 * Booking status transitions defined in SRS Section 4.5 (FR-BKG-06).
 */
public enum BookingStatus {
    Pending,
    Approved,
    Rejected,
    Cancelled,
    Completed
}
