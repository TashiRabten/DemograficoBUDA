package com.example.glossariobuda.exceptions;

/**
 * Base unchecked exception for glossary runtime errors.
 */
public class GlossaryRuntimeException extends RuntimeException {

    public GlossaryRuntimeException(String message) {
        super(message);
    }

    public GlossaryRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public GlossaryRuntimeException(Throwable cause) {
        super(cause);
    }
}
