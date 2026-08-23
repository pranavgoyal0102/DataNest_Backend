package com.datanest.repository;

import com.datanest.entity.FileMetadata;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRepository
        extends JpaRepository<FileMetadata, UUID> {

    List<FileMetadata> findByFirebaseUid(
            String firebaseUid
    );

    /**
     * Ownership-scoped lookup. An empty result means either "no such row" or
     * "not yours" - the caller must not distinguish between the two.
     */
    Optional<FileMetadata> findByIdAndFirebaseUid(
            UUID id,
            String firebaseUid
    );

    Optional<FileMetadata> findByFirebaseUidAndTitle(
            String firebaseUid,
            String title
    );

    List<FileMetadata> findByFirebaseUidAndTitleContainingIgnoreCase(
            String firebaseUid,
            String title
    );

    /** Search covers live rows only; the trash has its own endpoint. */
    List<FileMetadata> findByFirebaseUidAndIsDeletedFalseAndTitleContainingIgnoreCase(
            String firebaseUid,
            String title
    );

    List<FileMetadata> findByFirebaseUidAndIsDeletedFalse(
            String firebaseUid
    );

    List<FileMetadata> findByFirebaseUidAndIsDeletedTrue(
            String firebaseUid
    );

    /**
     * Cold start: strictly newer than the watermark. Soft-deleted rows are included on
     * purpose - they are the tombstones that let a deletion propagate.
     */
    @Query("""
            SELECT f FROM FileMetadata f
            WHERE f.firebaseUid = :uid
              AND f.updatedAt > :since
            ORDER BY f.updatedAt ASC, f.id ASC
            """)
    List<FileMetadata> findChangedSince(
            @Param("uid") String firebaseUid,
            @Param("since") long since,
            Pageable pageable
    );

    /**
     * Resume from a cursor. The id tiebreaker is what makes paging safe across rows that
     * share a millisecond: without it a page boundary inside a tied group silently skips
     * the remainder of that group.
     */
    @Query("""
            SELECT f FROM FileMetadata f
            WHERE f.firebaseUid = :uid
              AND (f.updatedAt > :since
                   OR (f.updatedAt = :since AND f.id > :lastId))
            ORDER BY f.updatedAt ASC, f.id ASC
            """)
    List<FileMetadata> findChangedAfterCursor(
            @Param("uid") String firebaseUid,
            @Param("since") long since,
            @Param("lastId") UUID lastId,
            Pageable pageable
    );
}