package com.datanest.controller;

import com.datanest.dto.request.CreateFileRequest;
import com.datanest.dto.request.UpdateFileRequest;
import com.datanest.dto.request.UploadFileRequest;
import com.datanest.dto.response.ApiResponse;
import com.datanest.dto.response.FileResponse;
import com.datanest.dto.response.SyncResponse;
import com.datanest.service.FileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping
    public ApiResponse<FileResponse> createFile(

            @AuthenticationPrincipal
            String uid,

            @Valid @RequestBody CreateFileRequest request
    ){

        FileResponse response =
                fileService.createFile(
                        uid,
                        request
                );

        return ApiResponse.<FileResponse>builder()
                .success(true)
                .message("File created successfully")
                .data(response)
                .build();
    }

    @PostMapping("/upload")
    public ApiResponse<FileResponse> uploadFile(

            @AuthenticationPrincipal
            String uid,

            @RequestPart("file")
            MultipartFile file,

            @ModelAttribute
            UploadFileRequest request
    ) {

        FileResponse response =
                fileService.uploadFile(
                        uid,
                        file,
                        request
                );

        return ApiResponse.<FileResponse>builder()
                .success(true)
                .message("File uploaded successfully")
                .data(response)
                .build();
    }

    @GetMapping
    public ApiResponse<List<FileResponse>> getFiles(

            @AuthenticationPrincipal
            String uid
    ){

        return ApiResponse.<List<FileResponse>>builder()
                .success(true)
                .message("Files fetched successfully")
                .data(fileService.getFiles(uid))
                .build();
    }

    /**
     * Delta sync. Literal /sync cannot collide with anything today because the old
     * GET /api/files/{uid} route was removed when auth landed.
     */
    @GetMapping("/sync")
    public ApiResponse<SyncResponse> sync(

            @AuthenticationPrincipal
            String uid,

            @RequestParam(required = false)
            Long since,

            @RequestParam(required = false)
            String cursor,

            @RequestParam(defaultValue = "100")
            int limit
    ) {

        return ApiResponse.<SyncResponse>builder()
                .success(true)
                .message("Changes fetched successfully")
                .data(
                        fileService.syncFiles(
                                uid,
                                since,
                                cursor,
                                limit
                        )
                )
                .build();
    }

    @GetMapping("/search")
    public ApiResponse<List<FileResponse>> searchFiles(

            @AuthenticationPrincipal
            String uid,

            @RequestParam
            String q
    ) {

        return ApiResponse.<List<FileResponse>>builder()
                .success(true)
                .message("Files fetched successfully")
                .data(
                        fileService.searchFiles(uid, q)
                )
                .build();
    }

    @GetMapping("/trash")
    public ApiResponse<List<FileResponse>> getTrashFiles(

            @AuthenticationPrincipal
            String uid
    ) {

        return ApiResponse.<List<FileResponse>>builder()
                .success(true)
                .message("Trash fetched successfully")
                .data(
                        fileService.getTrashFiles(uid)
                )
                .build();
    }

    @PostMapping("/{id}/trash")
    public ApiResponse<Object> moveToTrash(

            @AuthenticationPrincipal
            String uid,

            @PathVariable UUID id,

            @RequestParam Long version
    ) {

        fileService.moveToTrash(uid, id, version);

        return ApiResponse.builder()
                .success(true)
                .message("Moved to trash")
                .data(null)
                .build();
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<Object> restoreFile(

            @AuthenticationPrincipal
            String uid,

            @PathVariable UUID id,

            @RequestParam Long version
    ) {

        fileService.restoreFile(uid, id, version);

        return ApiResponse.builder()
                .success(true)
                .message("Restored")
                .data(null)
                .build();
    }

    /**
     * Permanent delete. Destroys the stored asset, so it is only allowed once the file is
     * already in the trash.
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Object> deleteFile(

            @AuthenticationPrincipal
            String uid,

            @PathVariable UUID id,

            @RequestParam Long version
    ) {

        fileService.deleteFilePermanently(uid, id, version);

        return ApiResponse.builder()
                .success(true)
                .message("Deleted")
                .data(null)
                .build();
    }

    @PatchMapping("/{id}")
    public ApiResponse<Object> updateFile(

            @AuthenticationPrincipal
            String uid,

            @PathVariable UUID id,

            @Valid @RequestBody
            UpdateFileRequest request
    ) {

        fileService.updateFile(
                uid,
                id,
                request
        );

        return ApiResponse.builder()
                .success(true)
                .message("Updated")
                .data(null)
                .build();
    }
}
