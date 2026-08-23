package com.datanest.service.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryStorageService implements StorageService {

    private final Cloudinary cloudinary;

    /**
     * Streams the upload in chunks. The previous implementation called
     * {@code file.getBytes()} for anything that was not a video, which pulls the whole file
     * into heap - a few concurrent large uploads was an OOM.
     */
    @Override
    public StoredAsset uploadFile(
            MultipartFile file
    ) {

        String resourceType =
                resourceTypeFor(
                        file.getContentType()
                );

        try (InputStream in = file.getInputStream()) {

            Map<?, ?> result =
                    cloudinary.uploader().uploadLarge(
                            in,
                            ObjectUtils.asMap(
                                    "resource_type", resourceType
                            )
                    );

            return new StoredAsset(
                    asString(result.get("secure_url")),
                    asString(result.get("public_id")),
                    resourceType
            );

        } catch (IOException e) {
            throw new StorageException(
                    "Upload failed", e
            );
        }
    }

    @Override
    public void delete(
            String publicId,
            String resourceType
    ) {

        try {

            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap(
                            "resource_type", resourceType
                    )
            );

        } catch (IOException e) {
            throw new StorageException(
                    "Failed to delete asset " + publicId, e
            );
        }
    }

    /**
     * Chunked upload needs an explicit resource type - "auto" sniffing is only available on
     * the single-shot upload. Deriving it here is needed anyway: the value has to be stored
     * so the asset can be addressed for deletion later.
     *
     * <p>Audio is deliberately mapped to "video"; that is the bucket Cloudinary keeps it in.
     */
    private String resourceTypeFor(
            String contentType
    ) {

        if (contentType == null) {
            return "raw";
        }

        if (contentType.startsWith("video/")
                || contentType.startsWith("audio/")) {
            return "video";
        }

        if (contentType.startsWith("image/")) {
            return "image";
        }

        return "raw";
    }

    private String asString(
            Object value
    ) {

        return value != null
                ? value.toString()
                : null;
    }
}
