package com.datanest.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SyncResponse {

    /** Ascending by updatedAt. Soft-deleted rows appear as tombstones (isDeleted = true). */
    private List<FileResponse> changes;

    /** Pass back as the `cursor` parameter on the next pull. Opaque. */
    private String cursor;

    /** The page was full - pull again immediately rather than waiting. */
    private boolean hasMore;
}
