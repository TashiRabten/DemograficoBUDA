package com.example.glossariobuda.exceptions;

/**
 * Base exception class for all glossary-related errors.
 */
public class GlossaryException extends Exception {

    public GlossaryException(String message) {
        super(message);
    }

    public GlossaryException(String message, Throwable cause) {
        super(message, cause);
    }

    public GlossaryException(Throwable cause) {
        super(cause);
    }
}
