package com.datanest.service.impl;

import com.datanest.dto.request.CreateFileRequest;
import com.datanest.dto.request.UpdateFileRequest;
import com.datanest.dto.request.UploadFileRequest;
import com.datanest.dto.response.FileResponse;
import com.datanest.dto.response.SyncResponse;
import com.datanest.entity.FileMetadata;
import com.datanest.exception.FileNotFoundException;
import com.datanest.exception.FileNotTrashedException;
import com.datanest.exception.FileVersionConflictException;
import com.datanest.repository.FileRepository;
import com.datanest.service.FileService;
import com.datanest.service.storage.StorageService;
import com.datanest.service.storage.StoredAsset;
import com.datanest.service.sync.SyncCursor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private static final int MIN_SYNC_LIMIT = 1;
    private static final int MAX_SYNC_LIMIT = 500;

    private final FileRepository fileRepository;
    private final StorageService storageService;

    @Override
    public FileResponse createFile(
            String firebaseUid,
            CreateFileRequest request
    ) {

        Optional<FileMetadata> existingFile =
                fileRepository.findByFirebaseUidAndTitle(
                        firebaseUid,
                        request.getTitle()
                );

        if (existingFile.isPresent()) {
            return mapToResponse(
                    existingFile.get()
            );
        }

        long now =
                System.currentTimeMillis();

        FileMetadata file =
                FileMetadata.builder()
                        .firebaseUid(firebaseUid)
                        .title(
                                request.getTitle()
                        )
                        .mimeType(
                                request.getMimeType()
                        )
                        .size(
                                request.getSize()
                        )
                        .isStarred(
                                request.getIsStarred()
                        )
                        .isDeleted(
                                request.getIsDeleted()
                        )
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        FileMetadata saved =
                fileRepository.save(file);

        return mapToResponse(saved);
    }

    @Override
    public FileResponse uploadFile(

            String firebaseUid,

            MultipartFile file,

            UploadFileRequest request
    ) {

        StoredAsset asset =
                storageService.uploadFile(file);

        try {

            long now =
                    System.currentTimeMillis();

            FileMetadata metadata =
                    FileMetadata.builder()
                            .firebaseUid(firebaseUid)
                            .title(
                                    file.getOriginalFilename()
                            )
                            .mimeType(
                                    file.getContentType()
                            )
                            .size(
                                    file.getSize()
                            )
                            .cloudUrl(
                                    asset.url()
                            )
                            .publicId(
                                    asset.publicId()
                            )
                            .resourceType(
                                    asset.resourceType()
                            )
                            .isStarred(
                                    request.getIsStarred()
                            )
                            .isDeleted(false)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();

            FileMetadata saved =
                    fileRepository.save(metadata);

            return mapToResponse(saved);

        } catch (RuntimeException ex) {

            // The upload already succeeded, so a failure here would leave an asset nobody
            // has a record of. Compensate by removing it.
            try {

                storageService.delete(
                        asset.publicId(),
                        asset.resourceType()
                );

            } catch (RuntimeException cleanupFailure) {

                // Never let a cleanup failure replace the exception that caused it - that
                // would report a storage error for what was really a database failure.
                log.warn(
                        "Orphaned asset {} after a failed metadata write",
                        asset.publicId(),
                        cleanupFailure
                );
            }

            throw ex;
        }
    }



    @Override
    public List<FileResponse> getFiles(
            String firebaseUid
    ) {

        return fileRepository
                .findByFirebaseUid(
                        firebaseUid
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<FileResponse> searchFiles(
            String firebaseUid,
            String query
    ) {

        return fileRepository
                .findByFirebaseUidAndIsDeletedFalseAndTitleContainingIgnoreCase(
                        firebaseUid,
                        query
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void moveToTrash(
            String firebaseUid,
            UUID fileId,
            Long version
    ) {

        FileMetadata file =
                requireOwned(firebaseUid, fileId);

        requireVersion(file, version);

        // Already trashed: honour the same no-op rule updateFile follows, so a repeated
        // trash does not bump version/updatedAt and churn every other device's feed.
        if (Boolean.TRUE.equals(file.getIsDeleted())) {
            return;
        }

        file.setIsDeleted(true);
        file.setUpdatedAt(
                System.currentTimeMillis()
        );

        fileRepository.save(file);
    }

    @Override
    public void restoreFile(
            String firebaseUid,
            UUID fileId,
            Long version
    ) {

        FileMetadata file =
                requireOwned(firebaseUid, fileId);

        requireVersion(file, version);

        // Already live - same no-op rule as moveToTrash.
        if (Boolean.FALSE.equals(file.getIsDeleted())) {
            return;
        }

        file.setIsDeleted(false);
        file.setUpdatedAt(
                System.currentTimeMillis()
        );

        fileRepository.save(file);
    }

    @Override
    public List<FileResponse> getTrashFiles(
            String firebaseUid
    ) {

        return fileRepository
                .findByFirebaseUidAndIsDeletedTrue(
                        firebaseUid
                )
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void updateFile(
            String firebaseUid,
            UUID fileId,
            UpdateFileRequest request
    ) {

        FileMetadata file =
                requireOwned(firebaseUid, fileId);

        // Checked before the change detection below on purpose: a stale client gets a
        // conflict even when the values it sent happen to match, because its base
        // version is stale regardless of what it sent.
        requireVersion(file, request.getVersion());

        boolean changed = false;

        // A null field means "not supplied" - assigning it would wipe the stored value.
        // Nothing is mutated unless it actually differs, so the no-op path below cannot
        // be picked up by dirty checking.

        if (request.getTitle() != null
                && !request.getTitle().equals(file.getTitle())) {

            file.setTitle(
                    request.getTitle()
            );

            changed = true;
        }

        if (request.getIsDeleted() != null
                && !request.getIsDeleted().equals(file.getIsDeleted())) {

            file.setIsDeleted(
                    request.getIsDeleted()
            );

            changed = true;
        }

        if (request.getIsStarred() != null
                && !request.getIsStarred().equals(file.getIsStarred())) {

            file.setIsStarred(
                    request.getIsStarred()
            );

            changed = true;
        }

        // updatedAt drives delta sync, so a no-op request must not bump it.
        // This also means a no-op does not increment the version, so the client's
        // version stays valid.
        if (!changed) {
            return;
        }

        file.setUpdatedAt(
                System.currentTimeMillis()
        );

        // The comparison above cannot catch a writer that commits between our read and
        // this save; the versioned UPDATE can. Catching here relies on save() running in
        // its own transaction, which flushes inside the call. If @Transactional is ever
        // added to this class the flush moves to commit time, this catch stops firing,
        // and GlobalExceptionHandler's ObjectOptimisticLockingFailureException handler
        // becomes the backstop.
        try {

            fileRepository.save(file);

        } catch (ObjectOptimisticLockingFailureException ex) {

            FileMetadata current =
                    fileRepository
                            .findByIdAndFirebaseUid(
                                    fileId,
                                    firebaseUid
                            )
                            .orElseThrow(
                                    () -> new FileNotFoundException(
                                            "File not found"
                                    )
                            );

            throw new FileVersionConflictException(
                    mapToResponse(current)
            );
        }
    }

    /**
     * Delta sync.
     *
     * <p>The returned cursor is always derived from the last row actually returned, never
     * from "now". The watermark therefore cannot advance past data the client has really
     * received, so nothing between the last row and now can be stepped over.
     *
     * <p>Known limitation: updatedAt is stamped in application code before the write
     * commits, so a row stamped T can commit after one stamped T+5. A client already past
     * T+5 will not see the T row again. Deriving the cursor from returned rows narrows
     * this window but does not close it - closing it properly needs a monotonic,
     * database-assigned sequence to sync on instead of a wall-clock timestamp.
     */
    @Override
    public SyncResponse syncFiles(
            String firebaseUid,
            Long since,
            String cursor,
            int limit
    ) {

        int pageSize =
                Math.max(
                        MIN_SYNC_LIMIT,
                        Math.min(limit, MAX_SYNC_LIMIT)
                );

        Pageable page =
                PageRequest.of(0, pageSize);

        // No Sort on the Pageable - the ORDER BY lives in the query and a sort here
        // would be appended to it.

        List<FileMetadata> rows;
        SyncCursor incoming;

        if (cursor != null && !cursor.isBlank()) {

            incoming = SyncCursor.decode(cursor);

            rows = fileRepository.findChangedAfterCursor(
                    firebaseUid,
                    incoming.updatedAt(),
                    incoming.id(),
                    page
            );

        } else {

            long watermark =
                    since != null ? since : 0L;

            incoming = new SyncCursor(
                    watermark,
                    SyncCursor.ZERO_ID
            );

            rows = fileRepository.findChangedSince(
                    firebaseUid,
                    watermark,
                    page
            );
        }

        // Nothing seen means nothing should advance.
        SyncCursor next =
                rows.isEmpty()
                        ? incoming
                        : cursorFrom(rows.get(rows.size() - 1));

        return SyncResponse.builder()
                .changes(
                        rows.stream()
                                .map(this::mapToResponse)
                                .toList()
                )
                .cursor(next.encode())
                .hasMore(rows.size() == pageSize)
                .build();
    }

    private SyncCursor cursorFrom(
            FileMetadata file
    ) {

        return new SyncCursor(
                file.getUpdatedAt(),
                file.getId()
        );
    }

    /**
     * Destroys the asset first, then the row.
     *
     * <p>The order is deliberate and is the opposite of the intuitive one. Removing the row
     * first would mean a later asset-delete failure leaves an orphan whose publicId is no
     * longer recorded anywhere - unrecoverable. Doing the asset first keeps the row as the
     * recovery handle until the irreversible step has succeeded: if destroy fails the row
     * is untouched and the client retries; if the row delete then fails, a retry re-runs
     * destroy harmlessly, since it is idempotent.
     *
     * <p>Known interaction with delta sync: this hard-deletes the row, so no tombstone
     * remains. A client offline across both the trash and the delete never learns the file
     * is gone. The trash-first gate narrows this - the tombstone exists for the whole
     * window between the two calls - but closing it properly needs retained tombstone rows
     * or a separate deletions table.
     */
    @Override
    public void deleteFilePermanently(
            String firebaseUid,
            UUID fileId,
            Long version
    ) {

        FileMetadata file =
                requireOwned(firebaseUid, fileId);

        requireVersion(file, version);

        if (!Boolean.TRUE.equals(file.getIsDeleted())) {
            throw new FileNotTrashedException(
                    "File must be moved to trash before it can be permanently deleted"
            );
        }

        String publicId = file.getPublicId();
        String resourceType = file.getResourceType();

        // Row first, asset second. The versioned DELETE is what makes this safe under
        // concurrency: if another device restored the file after our read, it affects no
        // rows and raises a lock failure here - before anything irreversible happens.
        // Destroying the asset first would leave a live row pointing at destroyed bytes,
        // which the user can see and cannot repair.
        fileRepository.delete(file);

        if (publicId != null) {
            try {

                storageService.delete(publicId, resourceType);

            } catch (RuntimeException ex) {

                // The row is already gone, so the id is logged rather than lost - the
                // same recovery handle the compensating delete in uploadFile relies on.
                log.warn(
                        "Orphaned asset {} ({}) - row deleted but destroy failed",
                        publicId,
                        resourceType,
                        ex
                );
            }
        }
    }

    /**
     * One version rule for every mutation. Objects.equals so a null version cannot NPE if
     * a caller reaches this outside the validated controller path.
     */
    private void requireVersion(
            FileMetadata file,
            Long version
    ) {

        if (!Objects.equals(version, file.getVersion())) {
            throw new FileVersionConflictException(
                    mapToResponse(file)
            );
        }
    }

    /**
     * Loads a row only if it belongs to the caller. A row owned by someone else is
     * reported as missing so file IDs cannot be probed for existence.
     */
    private FileMetadata requireOwned(
            String firebaseUid,
            UUID fileId
    ) {

        return fileRepository
                .findByIdAndFirebaseUid(
                        fileId,
                        firebaseUid
                )
                .orElseThrow(
                        () -> new FileNotFoundException(
                                "File not found"
                        )
                );
    }

    private FileResponse mapToResponse(
            FileMetadata file
    ) {

        return FileResponse.builder()
                .id(file.getId())
                .title(file.getTitle())
                .mimeType(file.getMimeType())
                .size(file.getSize())
                .cloudUrl(file.getCloudUrl())
                .isStarred(file.getIsStarred())
                .isDeleted(file.getIsDeleted())
                .createdAt(file.getCreatedAt())
                .updatedAt(file.getUpdatedAt())
                .version(file.getVersion())
                .build();
    }
}
