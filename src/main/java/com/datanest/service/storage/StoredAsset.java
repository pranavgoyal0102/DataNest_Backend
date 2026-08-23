package com.datanest.service.storage;

/**
 * What the storage backend gives back after a successful upload.
 *
 * <p>{@code publicId} and {@code resourceType} are both required to delete the asset later -
 * Cloudinary's destroy call needs the id and the bucket it lives in. Storing only the URL,
 * as this used to, leaves the asset permanently undeletable.
 */
public record StoredAsset(
        String url,
        String publicId,
        String resourceType
) {
}
