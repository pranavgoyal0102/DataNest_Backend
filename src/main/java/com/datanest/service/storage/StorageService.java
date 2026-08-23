package com.datanest.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface StorageService {

    StoredAsset uploadFile(
            MultipartFile file
    );

    /**
     * Removes an asset from the backend.
     *
     * <p>Throws on failure rather than swallowing. Both callers want to catch it but want to
     * do different things - one is compensating for a failed metadata write, the other is a
     * user-requested delete - so the policy belongs at the call site, not hidden in here.
     */
    void delete(
            String publicId,
            String resourceType
    );
}
