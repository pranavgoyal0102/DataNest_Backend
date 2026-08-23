package com.datanest.service.sync;

import com.datanest.exception.InvalidSyncCursorException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Opaque pagination cursor over (updatedAt, id).
 *
 * <p>A timestamp alone cannot page safely: rows routinely share a millisecond, so resuming
 * with {@code updatedAt > last} skips the rest of a tied group while {@code >=} re-sends it
 * forever. Pairing the timestamp with the row id gives a total order.
 *
 * <p>Clients treat the encoded form as opaque, so the encoding can change without an API break.
 */
public record SyncCursor(
        long updatedAt,
        UUID id
) {

    private static final String SEPARATOR = ":";

    /** Stands in for "no row seen yet" when a cold start produced an empty page. */
    public static final UUID ZERO_ID =
            new UUID(0L, 0L);

    public String encode() {

        String raw =
                updatedAt + SEPARATOR + id;

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                        raw.getBytes(StandardCharsets.UTF_8)
                );
    }

    public static SyncCursor decode(
            String encoded
    ) {

        if (encoded == null || encoded.isBlank()) {
            throw new InvalidSyncCursorException(
                    "cursor must not be blank"
            );
        }

        String raw;

        try {
            raw = new String(
                    Base64.getUrlDecoder().decode(encoded),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException ex) {
            throw new InvalidSyncCursorException(
                    "cursor is not valid base64"
            );
        }

        int separator =
                raw.indexOf(SEPARATOR);

        if (separator < 0) {
            throw new InvalidSyncCursorException(
                    "cursor is malformed"
            );
        }

        try {

            return new SyncCursor(
                    Long.parseLong(
                            raw.substring(0, separator)
                    ),
                    UUID.fromString(
                            raw.substring(separator + 1)
                    )
            );

        } catch (IllegalArgumentException ex) {
            throw new InvalidSyncCursorException(
                    "cursor is malformed"
            );
        }
    }
}
