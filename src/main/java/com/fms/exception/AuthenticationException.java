package com.fms.exception;

/**
 * Thrown on invalid credentials, deactivated accounts, or locked accounts.
 * Aligns with SRS Section 8, BR-11, and FR-AUTH-07.
 */
public class AuthenticationException extends Exception {
    private final boolean isLocked;

    public AuthenticationException(String message) {
        super(message);
        this.isLocked = false;
    }

    public AuthenticationException(String message, boolean isLocked) {
        super(message);
        this.isLocked = isLocked;
    }

    public boolean isLocked() {
        return isLocked;
    }
}
