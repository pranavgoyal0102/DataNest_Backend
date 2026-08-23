package com.datanest.service.sync;

import com.datanest.exception.InvalidSyncCursorException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SyncCursorTest {

    private static String base64(String raw) {

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a cursor round-trips through encode and decode")
    void roundTrips() {

        SyncCursor original =
                new SyncCursor(1_700_000_000_000L, UUID.randomUUID());

        assertThat(SyncCursor.decode(original.encode()))
                .isEqualTo(original);
    }

    @Test
    @DisplayName("the encoded form does not expose the raw pair")
    void encodedFormIsOpaque() {

        String encoded =
                new SyncCursor(1234L, UUID.randomUUID()).encode();

        assertThat(encoded).doesNotContain(":", "1234");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not base64 %%%", "", "   "})
    @DisplayName("unusable input is rejected")
    void rejectsUnusableInput(String cursor) {

        assertThatThrownBy(() -> SyncCursor.decode(cursor))
                .isInstanceOf(InvalidSyncCursorException.class);
    }

    @Test
    @DisplayName("null is rejected")
    void rejectsNull() {

        assertThatThrownBy(() -> SyncCursor.decode(null))
                .isInstanceOf(InvalidSyncCursorException.class);
    }

    @Test
    @DisplayName("valid base64 with no separator is rejected")
    void rejectsMissingSeparator() {

        assertThatThrownBy(() -> SyncCursor.decode(base64("1234")))
                .isInstanceOf(InvalidSyncCursorException.class);
    }

    @Test
    @DisplayName("a non-numeric timestamp is rejected")
    void rejectsBadTimestamp() {

        assertThatThrownBy(() ->
                SyncCursor.decode(base64("abc:" + UUID.randomUUID()))
        ).isInstanceOf(InvalidSyncCursorException.class);
    }

    @Test
    @DisplayName("a malformed uuid is rejected")
    void rejectsBadUuid() {

        assertThatThrownBy(() -> SyncCursor.decode(base64("1234:not-a-uuid")))
                .isInstanceOf(InvalidSyncCursorException.class);
    }
}
