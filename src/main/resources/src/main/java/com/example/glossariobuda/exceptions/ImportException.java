package com.example.glossariobuda.exceptions;

/**
 * Exception thrown when import/export operations fail.
 */
public class ImportException extends GlossaryException {

    public ImportException(String message) {
        super(message);
    }

    public ImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
