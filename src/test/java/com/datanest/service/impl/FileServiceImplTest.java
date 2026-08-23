package com.datanest.service.impl;

import com.datanest.dto.request.UpdateFileRequest;
import com.datanest.dto.response.SyncResponse;
import com.datanest.entity.FileMetadata;
import com.datanest.dto.request.UploadFileRequest;
import com.datanest.exception.FileNotFoundException;
import com.datanest.exception.FileNotTrashedException;
import com.datanest.exception.FileVersionConflictException;
import com.datanest.repository.FileRepository;
import com.datanest.service.storage.StorageException;
import com.datanest.service.storage.StorageService;
import com.datanest.service.storage.StoredAsset;
import com.datanest.service.sync.SyncCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    private static final String UID = "uid-owner";
    private static final long STORED_UPDATED_AT = 1_000L;
    private static final long STORED_VERSION = 3L;
    private static final long STALE_VERSION = 2L;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private FileServiceImpl fileService;

    private UUID fileId;
    private FileMetadata stored;

    @BeforeEach
    void setUp() {

        fileId = UUID.randomUUID();

        stored = FileMetadata.builder()
                .id(fileId)
                .firebaseUid(UID)
                .title("original")
                .isStarred(true)
                .isDeleted(false)
                .createdAt(500L)
                .updatedAt(STORED_UPDATED_AT)
                .version(STORED_VERSION)
                .build();
    }

    private void givenOwned() {

        when(fileRepository.findByIdAndFirebaseUid(fileId, UID))
                .thenReturn(Optional.of(stored));
    }

    /** A request carrying the version the server currently holds. */
    private UpdateFileRequest currentVersionRequest() {

        UpdateFileRequest request = new UpdateFileRequest();
        request.setVersion(STORED_VERSION);
        return request;
    }

    // --- partial update semantics -------------------------------------------------

    @Test
    @DisplayName("sending only title leaves isStarred and isDeleted untouched")
    void partialUpdateWithTitleOnlyDoesNotWipeFlags() {

        givenOwned();

        UpdateFileRequest request = currentVersionRequest();
        request.setTitle("renamed");

        fileService.updateFile(UID, fileId, request);

        assertThat(stored.getTitle()).isEqualTo("renamed");
        assertThat(stored.getIsStarred()).isTrue();
        assertThat(stored.getIsDeleted()).isFalse();
        assertThat(stored.getUpdatedAt()).isGreaterThan(STORED_UPDATED_AT);

        verify(fileRepository).save(stored);
    }

    @Test
    @DisplayName("sending only isStarred leaves title untouched")
    void partialUpdateWithFlagOnlyDoesNotWipeTitle() {

        givenOwned();

        UpdateFileRequest request = currentVersionRequest();
        request.setIsStarred(false);

        fileService.updateFile(UID, fileId, request);

        assertThat(stored.getTitle()).isEqualTo("original");
        assertThat(stored.getIsStarred()).isFalse();

        verify(fileRepository).save(stored);
    }

    @Test
    @DisplayName("explicit false is applied, not mistaken for an absent field")
    void explicitFalseIsApplied() {

        givenOwned();

        UpdateFileRequest request = currentVersionRequest();
        request.setIsStarred(false);

        fileService.updateFile(UID, fileId, request);

        assertThat(stored.getIsStarred()).isFalse();
        verify(fileRepository).save(stored);
    }

    @Test
    @DisplayName("a body with only a version changes nothing and does not save")
    void emptyRequestIsANoOp() {

        givenOwned();

        fileService.updateFile(UID, fileId, currentVersionRequest());

        assertThat(stored.getTitle()).isEqualTo("original");
        assertThat(stored.getIsStarred()).isTrue();
        assertThat(stored.getIsDeleted()).isFalse();
        assertThat(stored.getUpdatedAt()).isEqualTo(STORED_UPDATED_AT);

        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("values identical to the stored row do not bump updatedAt")
    void unchangedValuesDoNotBumpUpdatedAt() {

        givenOwned();

        UpdateFileRequest request = currentVersionRequest();
        request.setTitle("original");
        request.setIsStarred(true);
        request.setIsDeleted(false);

        fileService.updateFile(UID, fileId, request);

        assertThat(stored.getUpdatedAt()).isEqualTo(STORED_UPDATED_AT);
        verify(fileRepository, never()).save(any());
    }

    // --- ownership ----------------------------------------------------------------

    @Test
    @DisplayName("a row owned by another user is reported as missing and never written")
    void updateOnAnotherUsersRowThrowsAndDoesNotSave() {

        when(fileRepository.findByIdAndFirebaseUid(fileId, "uid-attacker"))
                .thenReturn(Optional.empty());

        UpdateFileRequest request = currentVersionRequest();
        request.setTitle("hijacked");

        assertThatThrownBy(() ->
                fileService.updateFile("uid-attacker", fileId, request)
        )
                .isInstanceOf(FileNotFoundException.class)
                .hasMessage("File not found");

        verify(fileRepository, never()).save(any());
    }

    // --- optimistic locking -------------------------------------------------------

    @Test
    @DisplayName("a stale version conflicts and carries the server's current state")
    void staleVersionConflicts() {

        givenOwned();

        UpdateFileRequest request = new UpdateFileRequest();
        request.setVersion(STALE_VERSION);
        request.setTitle("from a stale copy");

        assertThatThrownBy(() ->
                fileService.updateFile(UID, fileId, request)
        )
                .isInstanceOf(FileVersionConflictException.class)
                .hasMessage("Version conflict")
                .satisfies(thrown -> {

                    var current = ((FileVersionConflictException) thrown).getCurrent();

                    assertThat(current.getVersion()).isEqualTo(STORED_VERSION);
                    assertThat(current.getTitle()).isEqualTo("original");
                    assertThat(current.getIsStarred()).isTrue();
                    assertThat(current.getIsDeleted()).isFalse();
                });

        assertThat(stored.getTitle()).isEqualTo("original");
        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("a stale version conflicts even when the values sent match current state")
    void staleVersionConflictsBeforeTheNoOpShortCircuit() {

        givenOwned();

        UpdateFileRequest request = new UpdateFileRequest();
        request.setVersion(STALE_VERSION);
        request.setTitle("original");
        request.setIsStarred(true);
        request.setIsDeleted(false);

        assertThatThrownBy(() ->
                fileService.updateFile(UID, fileId, request)
        )
                .isInstanceOf(FileVersionConflictException.class);

        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("a lock failure at save time is reported as a conflict, not a 500")
    void concurrentWriteAtSaveTimeBecomesAConflict() {

        givenOwned();

        when(fileRepository.save(any()))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        FileMetadata.class, fileId
                ));

        UpdateFileRequest request = currentVersionRequest();
        request.setTitle("renamed");

        assertThatThrownBy(() ->
                fileService.updateFile(UID, fileId, request)
        )
                .isInstanceOf(FileVersionConflictException.class)
                .hasMessage("Version conflict");
    }

    // --- delta sync ---------------------------------------------------------------

    private FileMetadata row(String title, long updatedAt) {

        return FileMetadata.builder()
                .id(UUID.randomUUID())
                .firebaseUid(UID)
                .title(title)
                .isStarred(false)
                .isDeleted(false)
                .createdAt(1L)
                .updatedAt(updatedAt)
                .version(0L)
                .build();
    }

    @Test
    @DisplayName("the cursor comes from the last row returned, not from now")
    void cursorComesFromTheLastRow() {

        FileMetadata last = row("b", 2_000L);

        when(fileRepository.findChangedSince(eq(UID), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(row("a", 1_000L), last));

        SyncResponse response =
                fileService.syncFiles(UID, 0L, null, 10);

        SyncCursor cursor =
                SyncCursor.decode(response.getCursor());

        assertThat(cursor.updatedAt()).isEqualTo(2_000L);
        assertThat(cursor.id()).isEqualTo(last.getId());
        assertThat(response.getChanges()).hasSize(2);
    }

    @Test
    @DisplayName("a full page reports hasMore so the client pulls again")
    void fullPageReportsHasMore() {

        when(fileRepository.findChangedSince(eq(UID), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(row("a", 1L), row("b", 2L)));

        assertThat(fileService.syncFiles(UID, 0L, null, 2).isHasMore())
                .isTrue();
    }

    @Test
    @DisplayName("a short page reports no more")
    void shortPageReportsNoMore() {

        when(fileRepository.findChangedSince(eq(UID), anyLong(), any(Pageable.class)))
                .thenReturn(List.of(row("a", 1L)));

        assertThat(fileService.syncFiles(UID, 0L, null, 10).isHasMore())
                .isFalse();
    }

    @Test
    @DisplayName("an empty page hands back the cursor the client sent")
    void emptyPageEchoesIncomingCursor() {

        String incoming =
                new SyncCursor(5_000L, UUID.randomUUID()).encode();

        when(fileRepository.findChangedAfterCursor(
                eq(UID), anyLong(), any(UUID.class), any(Pageable.class)
        )).thenReturn(List.of());

        SyncResponse response =
                fileService.syncFiles(UID, null, incoming, 10);

        assertThat(response.getChanges()).isEmpty();
        assertThat(response.getCursor()).isEqualTo(incoming);
    }

    @Test
    @DisplayName("a cursor takes precedence over since when both are sent")
    void cursorWinsOverSince() {

        String incoming =
                new SyncCursor(5_000L, UUID.randomUUID()).encode();

        when(fileRepository.findChangedAfterCursor(
                eq(UID), anyLong(), any(UUID.class), any(Pageable.class)
        )).thenReturn(List.of());

        fileService.syncFiles(UID, 0L, incoming, 10);

        verify(fileRepository, never())
                .findChangedSince(any(), anyLong(), any(Pageable.class));
    }

    @Test
    @DisplayName("limit is clamped at both ends rather than rejected")
    void limitIsClamped() {

        when(fileRepository.findChangedSince(eq(UID), anyLong(), any(Pageable.class)))
                .thenReturn(List.of());

        fileService.syncFiles(UID, 0L, null, 0);
        fileService.syncFiles(UID, 0L, null, 10_000);

        ArgumentCaptor<Pageable> captor =
                ArgumentCaptor.forClass(Pageable.class);

        verify(fileRepository, times(2))
                .findChangedSince(eq(UID), anyLong(), captor.capture());

        assertThat(captor.getAllValues())
                .extracting(Pageable::getPageSize)
                .containsExactly(1, 500);
    }

    // --- upload: compensating delete -----------------------------------------------

    private static final StoredAsset ASSET =
            new StoredAsset("https://cdn/x.png", "folder/x", "image");

    private MockMultipartFile upload() {

        return new MockMultipartFile(
                "file", "x.png", "image/png", "payload".getBytes()
        );
    }

    private void givenUploadSucceeds() {

        when(storageService.uploadFile(any())).thenReturn(ASSET);
    }

    @Test
    @DisplayName("a successful upload stores the handles needed to delete the asset later")
    void uploadStoresAssetHandles() {

        givenUploadSucceeds();
        when(fileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        fileService.uploadFile(UID, upload(), new UploadFileRequest());

        ArgumentCaptor<FileMetadata> captor =
                ArgumentCaptor.forClass(FileMetadata.class);

        verify(fileRepository).save(captor.capture());

        assertThat(captor.getValue().getPublicId()).isEqualTo("folder/x");
        assertThat(captor.getValue().getResourceType()).isEqualTo("image");
        assertThat(captor.getValue().getCloudUrl()).isEqualTo("https://cdn/x.png");

        verify(storageService, never()).delete(any(), any());
    }

    @Test
    @DisplayName("a failed metadata write deletes the asset it would have orphaned")
    void failedMetadataWriteCompensates() {

        givenUploadSucceeds();
        when(fileRepository.save(any()))
                .thenThrow(new IllegalStateException("database is down"));

        assertThatThrownBy(() ->
                fileService.uploadFile(UID, upload(), new UploadFileRequest())
        ).isInstanceOf(IllegalStateException.class);

        verify(storageService).delete("folder/x", "image");
    }

    @Test
    @DisplayName("the original failure propagates, not the storage error")
    void originalFailurePropagates() {

        givenUploadSucceeds();
        when(fileRepository.save(any()))
                .thenThrow(new IllegalStateException("database is down"));

        assertThatThrownBy(() ->
                fileService.uploadFile(UID, upload(), new UploadFileRequest())
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database is down");
    }

    @Test
    @DisplayName("a cleanup failure does not mask the failure that caused it")
    void cleanupFailureDoesNotMaskTheOriginal() {

        givenUploadSucceeds();
        when(fileRepository.save(any()))
                .thenThrow(new IllegalStateException("database is down"));
        doThrow(new StorageException("cloudinary is down too", null))
                .when(storageService).delete(any(), any());

        assertThatThrownBy(() ->
                fileService.uploadFile(UID, upload(), new UploadFileRequest())
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database is down");
    }

    // --- permanent delete ----------------------------------------------------------

    private void givenTrashed() {

        stored.setIsDeleted(true);
        stored.setPublicId("folder/x");
        stored.setResourceType("image");
    }

    @Test
    @DisplayName("permanent delete removes the row before destroying the asset")
    void permanentDeleteRemovesRowBeforeAsset() {

        givenOwned();
        givenTrashed();

        fileService.deleteFilePermanently(UID, fileId, STORED_VERSION);

        // Row first: the versioned DELETE is the concurrency guard. If another device
        // restored the file after our read it fails here, before the bytes are gone.
        InOrder order = inOrder(fileRepository, storageService);
        order.verify(fileRepository).delete(stored);
        order.verify(storageService).delete("folder/x", "image");
    }

    @Test
    @DisplayName("a concurrent restore is caught before the asset is destroyed")
    void concurrentRestoreStopsTheAssetBeingDestroyed() {

        givenOwned();
        givenTrashed();

        // Another device restored the file after our read, so the versioned delete
        // affects no rows.
        doThrow(new ObjectOptimisticLockingFailureException(FileMetadata.class, fileId))
                .when(fileRepository).delete(stored);

        assertThatThrownBy(() ->
                fileService.deleteFilePermanently(UID, fileId, STORED_VERSION)
        ).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        verify(storageService, never()).delete(any(), any());
    }

    @Test
    @DisplayName("a failed destroy after the row is gone does not fail the request")
    void assetDestroyFailureAfterRowDeleteIsSwallowed() {

        givenOwned();
        givenTrashed();

        doThrow(new StorageException("cloudinary is down", null))
                .when(storageService).delete(any(), any());

        fileService.deleteFilePermanently(UID, fileId, STORED_VERSION);

        verify(fileRepository).delete(stored);
        verify(storageService).delete("folder/x", "image");
    }

    @Test
    @DisplayName("a live file cannot be permanently deleted")
    void permanentDeleteRequiresTrashFirst() {

        givenOwned();

        assertThatThrownBy(() ->
                fileService.deleteFilePermanently(UID, fileId, STORED_VERSION)
        ).isInstanceOf(FileNotTrashedException.class);

        verify(storageService, never()).delete(any(), any());
        verify(fileRepository, never()).delete(any(FileMetadata.class));
    }

    @Test
    @DisplayName("a stale version blocks permanent delete")
    void permanentDeleteRejectsStaleVersion() {

        givenOwned();
        givenTrashed();

        assertThatThrownBy(() ->
                fileService.deleteFilePermanently(UID, fileId, STALE_VERSION)
        ).isInstanceOf(FileVersionConflictException.class);

        verify(storageService, never()).delete(any(), any());
        verify(fileRepository, never()).delete(any(FileMetadata.class));
    }

    @Test
    @DisplayName("another user cannot permanently delete a row")
    void permanentDeleteEnforcesOwnership() {

        when(fileRepository.findByIdAndFirebaseUid(fileId, "uid-attacker"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                fileService.deleteFilePermanently("uid-attacker", fileId, STORED_VERSION)
        ).isInstanceOf(FileNotFoundException.class);

        verify(storageService, never()).delete(any(), any());
        verify(fileRepository, never()).delete(any(FileMetadata.class));
    }

    @Test
    @DisplayName("a row with no asset behind it still deletes")
    void permanentDeleteWithNoAsset() {

        givenOwned();
        stored.setIsDeleted(true);

        fileService.deleteFilePermanently(UID, fileId, STORED_VERSION);

        verify(storageService, never()).delete(any(), any());
        verify(fileRepository).delete(stored);
    }

    // --- trash / restore version checks ---------------------------------------------

    @Test
    @DisplayName("trash rejects a stale version")
    void trashRejectsStaleVersion() {

        givenOwned();

        assertThatThrownBy(() ->
                fileService.moveToTrash(UID, fileId, STALE_VERSION)
        ).isInstanceOf(FileVersionConflictException.class);

        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("restore rejects a stale version")
    void restoreRejectsStaleVersion() {

        givenOwned();

        assertThatThrownBy(() ->
                fileService.restoreFile(UID, fileId, STALE_VERSION)
        ).isInstanceOf(FileVersionConflictException.class);

        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("trash with the current version soft-deletes the row")
    void trashWithCurrentVersionSucceeds() {

        givenOwned();

        fileService.moveToTrash(UID, fileId, STORED_VERSION);

        assertThat(stored.getIsDeleted()).isTrue();
        verify(fileRepository).save(stored);
    }

    @Test
    @DisplayName("trashing an already-trashed file changes nothing and does not save")
    void redundantTrashIsANoOp() {

        givenOwned();
        stored.setIsDeleted(true);

        fileService.moveToTrash(UID, fileId, STORED_VERSION);

        assertThat(stored.getUpdatedAt()).isEqualTo(STORED_UPDATED_AT);
        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("restoring an already-live file changes nothing and does not save")
    void redundantRestoreIsANoOp() {

        givenOwned();

        fileService.restoreFile(UID, fileId, STORED_VERSION);

        assertThat(stored.getUpdatedAt()).isEqualTo(STORED_UPDATED_AT);
        verify(fileRepository, never()).save(any());
    }

    @Test
    @DisplayName("restoring a trashed file does save")
    void restoringTrashedFileSaves() {

        givenOwned();
        stored.setIsDeleted(true);

        fileService.restoreFile(UID, fileId, STORED_VERSION);

        assertThat(stored.getIsDeleted()).isFalse();
        verify(fileRepository).save(stored);
    }

    // --- search ----------------------------------------------------------------------

    @Test
    @DisplayName("search looks at live rows only")
    void searchExcludesTrashed() {

        when(fileRepository
                .findByFirebaseUidAndIsDeletedFalseAndTitleContainingIgnoreCase(UID, "holiday"))
                .thenReturn(List.of(stored));

        assertThat(fileService.searchFiles(UID, "holiday")).hasSize(1);

        verify(fileRepository, never())
                .findByFirebaseUidAndTitleContainingIgnoreCase(any(), any());
    }
}
