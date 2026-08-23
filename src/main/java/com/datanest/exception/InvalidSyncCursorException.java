package com.datanest.exception;

/**
 * A sync cursor the server did not issue, or one that has been corrupted in transit.
 * Has its own type so the catch-all handler cannot report a client typo as a 500.
 */
public class InvalidSyncCursorException
        extends RuntimeException {

    public InvalidSyncCursorException(
            String message
    ) {
        super(message);
    }
}
