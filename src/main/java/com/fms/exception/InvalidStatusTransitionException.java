package com.fms.exception;

/**
 * Unchecked exception thrown when an illegal status change is attempted on bookings or maintenance requests.
 * Aligns with SRS Section 8, BR-09, and FR-MNT-03.
 */
public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
