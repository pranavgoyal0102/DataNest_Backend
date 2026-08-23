package com.datanest.service;

import com.datanest.dto.request.CreateFileRequest;
import com.datanest.dto.request.UpdateFileRequest;
import com.datanest.dto.request.UploadFileRequest;
import com.datanest.dto.response.FileResponse;
import com.datanest.dto.response.SyncResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Every method takes the caller's Firebase UID as its first argument. That UID must come
 * from the verified ID token (the authentication principal), never from a request body
 * or path variable.
 */
public interface FileService {

    FileResponse createFile(
            String firebaseUid,
            CreateFileRequest request
    );

    List<FileResponse> getFiles(
            String firebaseUid
    );

    List<FileResponse> searchFiles(
            String firebaseUid,
            String query
    );

    FileResponse uploadFile(
            String firebaseUid,
            MultipartFile file,
            UploadFileRequest request
    );

    void moveToTrash(
            String firebaseUid,
            UUID fileId,
            Long version
    );

    void restoreFile(
            String firebaseUid,
            UUID fileId,
            Long version
    );

    /**
     * Destroys the stored asset and removes the row. Only permitted once the file is
     * already in the trash - the asset deletion cannot be undone.
     */
    void deleteFilePermanently(
            String firebaseUid,
            UUID fileId,
            Long version
    );

    List<FileResponse> getTrashFiles(
            String firebaseUid
    );

    void updateFile(
            String firebaseUid,
            UUID fileId,
            UpdateFileRequest request
    );

    /**
     * Rows changed since a watermark, ascending, tombstones included.
     *
     * @param since  epoch millis; used only on a cold start, ignored when cursor is set
     * @param cursor opaque cursor from a previous pull; wins over since when both are given
     * @param limit  clamped to [1, 500]
     */
    SyncResponse syncFiles(
            String firebaseUid,
            Long since,
            String cursor,
            int limit
    );
}
