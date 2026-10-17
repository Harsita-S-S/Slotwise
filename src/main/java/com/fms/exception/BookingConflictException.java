package com.fms.exception;

/**
 * Thrown when a requested booking slot clashes with an existing booking or scheduled maintenance.
 * Aligns with SRS Section 8 and FR-BKG-04 / BR-01.
 */
public class BookingConflictException extends Exception {
    private final int facilityId;
    private final String bookingDate;
    private final int slotNo;

    public BookingConflictException(String message, int facilityId, String bookingDate, int slotNo) {
        super(message);
        this.facilityId = facilityId;
        this.bookingDate = bookingDate;
        this.slotNo = slotNo;
    }

    public BookingConflictException(String message) {
        super(message);
        this.facilityId = 0;
        this.bookingDate = "";
        this.slotNo = 0;
    }

    public int getFacilityId() {
        return facilityId;
    }

    public String getBookingDate() {
        return bookingDate;
    }

    public int getSlotNo() {
        return slotNo;
    }
}
