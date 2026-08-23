package com.datanest.exception;

import com.datanest.dto.response.FileResponse;
import lombok.Getter;

/**
 * Raised when a client updates a file from a stale copy. Carries the server's current
 * state so the 409 can hand it back in the response body and the client can reconcile
 * without a second round trip.
 */
@Getter
public class FileVersionConflictException
        extends RuntimeException {

    private final transient FileResponse current;

    public FileVersionConflictException(
            FileResponse current
    ) {
        super("Version conflict");
        this.current = current;
    }
}
