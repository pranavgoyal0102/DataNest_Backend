package com.datanest.exception;

/**
 * Permanent delete was attempted on a row that is not in the trash. Destroying an asset is
 * irreversible, so it is deliberately a two-step operation.
 */
public class FileNotTrashedException
        extends RuntimeException {

    public FileNotTrashedException(
            String message
    ) {
        super(message);
    }
}
