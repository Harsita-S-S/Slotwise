package com.fms.exception;

/**
 * Thrown when a facility is inactive, under maintenance, or inside a maintenance window.
 * Aligns with SRS Section 8 and BR-06.
 */
public class ResourceUnavailableException extends Exception {
    public ResourceUnavailableException(String message) {
        super(message);
    }
}
