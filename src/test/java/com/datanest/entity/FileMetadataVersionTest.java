package com.datanest.entity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs real Hibernate against H2. The mocked tests in FileServiceImplTest cover our own
 * version comparison; only this one proves @Version itself behaves as assumed.
 *
 * @DataJpaTest loads the JPA slice only, so FirebaseConfig and CloudinaryConfig are never
 * instantiated and no credentials are needed.
 */
@DataJpaTest
class FileMetadataVersionTest {

    @Autowired
    private TestEntityManager testEntityManager;

    private FileMetadata persistedFile() {

        return testEntityManager.persistFlushFind(
                FileMetadata.builder()
                        .firebaseUid("uid-owner")
                        .title("original")
                        .isStarred(false)
                        .isDeleted(false)
                        .createdAt(1L)
                        .updatedAt(1L)
                        .build()
        );
    }

    @Test
    @DisplayName("a newly persisted row starts at version 0")
    void newRowStartsAtVersionZero() {

        assertThat(persistedFile().getVersion()).isZero();
    }

    @Test
    @DisplayName("each update increments the version")
    void updateIncrementsVersion() {

        FileMetadata file = persistedFile();

        file.setTitle("renamed");
        testEntityManager.flush();

        assertThat(file.getVersion()).isEqualTo(1L);

        file.setTitle("renamed again");
        testEntityManager.flush();

        assertThat(file.getVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("writing from a stale copy raises OptimisticLockException")
    void staleWriteIsRejected() {

        EntityManager em = testEntityManager.getEntityManager();

        FileMetadata file = persistedFile();
        UUID id = file.getId();

        // Take a copy at version 0, then detach it so it stops tracking the row.
        FileMetadata stale = em.find(FileMetadata.class, id);
        em.detach(stale);
        assertThat(stale.getVersion()).isZero();

        // Another writer moves the row to version 1.
        FileMetadata fresh = em.find(FileMetadata.class, id);
        fresh.setTitle("written by the other device");
        em.flush();
        assertThat(fresh.getVersion()).isEqualTo(1L);

        // The stale copy now writes over it - this is the conflict we must detect.
        stale.setTitle("written from a stale copy");

        assertThatThrownBy(() -> {
            em.merge(stale);
            em.flush();
        }).isInstanceOf(OptimisticLockException.class);
    }
}
