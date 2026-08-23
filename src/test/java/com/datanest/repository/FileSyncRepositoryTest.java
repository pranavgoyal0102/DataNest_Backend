package com.datanest.repository;

import com.datanest.entity.FileMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real keyset queries against H2. The point of these tests is paging correctness
 * across rows sharing an updatedAt - the case a timestamp-only cursor gets wrong.
 */
@DataJpaTest
class FileSyncRepositoryTest {

    private static final String UID = "uid-owner";

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TestEntityManager testEntityManager;

    private FileMetadata persist(
            String uid,
            String title,
            long updatedAt,
            boolean deleted
    ) {

        return testEntityManager.persistFlushFind(
                FileMetadata.builder()
                        .firebaseUid(uid)
                        .title(title)
                        .isStarred(false)
                        .isDeleted(deleted)
                        .createdAt(1L)
                        .updatedAt(updatedAt)
                        .build()
        );
    }

    /**
     * Walks the feed the way a client would: cold start, then follow the cursor until a
     * page comes back short. Duplicates are kept so a caller can assert none occurred.
     */
    private List<String> walk(int limit, long since) {

        List<String> seen = new ArrayList<>();

        List<FileMetadata> page =
                fileRepository.findChangedSince(
                        UID, since, PageRequest.of(0, limit)
                );

        while (!page.isEmpty()) {

            page.forEach(f -> seen.add(f.getTitle()));

            FileMetadata last =
                    page.get(page.size() - 1);

            if (page.size() < limit) {
                break;
            }

            page = fileRepository.findChangedAfterCursor(
                    UID,
                    last.getUpdatedAt(),
                    last.getId(),
                    PageRequest.of(0, limit)
            );
        }

        return seen;
    }

    @Test
    @DisplayName("five rows sharing one millisecond page through exactly once")
    void tiedTimestampsPageWithoutSkippingOrRepeating() {

        for (int i = 0; i < 5; i++) {
            persist(UID, "tied-" + i, 1_000L, false);
        }

        List<String> seen = walk(2, 0L);

        assertThat(seen)
                .hasSize(5)
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrder(
                        "tied-0", "tied-1", "tied-2", "tied-3", "tied-4"
                );
    }

    @Test
    @DisplayName("mixed timestamps page through once each, ascending")
    void mixedTimestampsPageInOrder() {

        persist(UID, "a", 1_000L, false);
        persist(UID, "b", 2_000L, false);
        persist(UID, "c", 2_000L, false);
        persist(UID, "d", 3_000L, false);

        List<String> seen = walk(1, 0L);

        assertThat(seen).hasSize(4).doesNotHaveDuplicates();
        assertThat(seen.get(0)).isEqualTo("a");
        assertThat(seen.get(3)).isEqualTo("d");
    }

    @Test
    @DisplayName("soft-deleted rows come back as tombstones, not filtered out")
    void tombstonesAreIncluded() {

        persist(UID, "live", 1_000L, false);
        persist(UID, "deleted", 2_000L, true);

        List<FileMetadata> rows =
                fileRepository.findChangedSince(
                        UID, 0L, PageRequest.of(0, 10)
                );

        assertThat(rows).hasSize(2);
        assertThat(rows)
                .filteredOn(FileMetadata::getIsDeleted)
                .extracting(FileMetadata::getTitle)
                .containsExactly("deleted");
    }

    @Test
    @DisplayName("since is strictly greater - a row at exactly since is excluded")
    void sinceIsStrictlyGreater() {

        persist(UID, "at-watermark", 1_000L, false);
        persist(UID, "after", 1_001L, false);

        List<FileMetadata> rows =
                fileRepository.findChangedSince(
                        UID, 1_000L, PageRequest.of(0, 10)
                );

        assertThat(rows)
                .extracting(FileMetadata::getTitle)
                .containsExactly("after");
    }

    @Test
    @DisplayName("rows belonging to another user are never returned")
    void otherUsersRowsAreNotVisible() {

        persist(UID, "mine", 1_000L, false);
        persist("uid-other", "theirs", 1_000L, false);

        assertThat(fileRepository.findChangedSince(
                UID, 0L, PageRequest.of(0, 10)
        ))
                .extracting(FileMetadata::getTitle)
                .containsExactly("mine");
    }

    @Test
    @DisplayName("the cursor query also stays scoped to the owner")
    void cursorQueryStaysScopedToOwner() {

        FileMetadata mine = persist(UID, "mine", 1_000L, false);
        persist("uid-other", "theirs", 2_000L, false);

        List<FileMetadata> rows =
                fileRepository.findChangedAfterCursor(
                        UID,
                        mine.getUpdatedAt(),
                        mine.getId(),
                        PageRequest.of(0, 10)
                );

        assertThat(rows).isEmpty();
    }
}
