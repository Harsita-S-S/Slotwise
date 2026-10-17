package com.fms.exception;

/**
 * Unchecked exception thrown when a user attempts an action not permitted for their role.
 * Aligns with SRS Section 8, Section 2.1, and FR-AUTH-05.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
