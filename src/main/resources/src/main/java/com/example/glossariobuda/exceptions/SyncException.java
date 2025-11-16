package com.example.glossariobuda.exceptions;

/**
 * Exception thrown when synchronization operations fail.
 */
public class SyncException extends GlossaryException {

    public SyncException(String message) {
        super(message);
    }

    public SyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
