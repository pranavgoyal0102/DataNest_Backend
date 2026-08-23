package com.datanest.service.storage;

/**
 * A storage backend call failed. Replaces the bare RuntimeException the Cloudinary service
 * used to throw, so callers can catch storage failures specifically.
 */
public class StorageException
        extends RuntimeException {

    public StorageException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
