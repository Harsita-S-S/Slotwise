package com.fms.exception;

/**
 * Unchecked exception thrown upon validation failure on input forms.
 * Aligns with SRS Section 8 and FR-VAL-01.
 */
public class InvalidInputException extends RuntimeException {
    private final String fieldName;

    public InvalidInputException(String message) {
        super(message);
        this.fieldName = null;
    }

    public InvalidInputException(String fieldName, String message) {
        super(message);
        this.fieldName = fieldName;
    }

    public String getFieldName() {
        return fieldName;
    }
}
