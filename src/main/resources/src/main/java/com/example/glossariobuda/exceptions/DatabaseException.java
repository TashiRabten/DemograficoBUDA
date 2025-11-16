package com.example.glossariobuda.exceptions;

/**
 * Exception thrown when database operations fail.
 */
public class DatabaseException extends GlossaryException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
