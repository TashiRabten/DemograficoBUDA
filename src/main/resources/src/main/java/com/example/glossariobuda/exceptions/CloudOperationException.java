package com.example.glossariobuda.exceptions;

/**
 * Exception thrown when cloud operations fail.
 */
public class CloudOperationException extends GlossaryException {

    public CloudOperationException(String message) {
        super(message);
    }

    public CloudOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
