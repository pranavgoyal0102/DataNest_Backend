package com.datanest.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Builder
@Data
public class FileResponse {

    private UUID id;

    private String title;

    private String mimeType;

    private Long size;

    private String cloudUrl;

    private Boolean isStarred;

    private Boolean isDeleted;

    private Long createdAt;

    private Long updatedAt;

    /** The value a client echoes back on the next PATCH of this file. */
    private Long version;
}