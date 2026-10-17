package com.fms.exception;

/**
 * Checked exception wrapping underlying SQLExceptions, connection failures, or constraint violations.
 * Aligns with SRS Section 8 (wraps SQLException, handles lost database connection gracefully).
 */
public class DatabaseException extends Exception {
    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
