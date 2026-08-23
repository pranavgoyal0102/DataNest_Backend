package com.datanest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Partial update. A null field means "not supplied" and leaves the stored value alone,
 * so omitting a field never clears it.
 */
@Data
public class UpdateFileRequest {

    @Pattern(
            regexp = ".*\\S.*",
            message = "title must not be blank"
    )
    @Size(
            max = 255,
            message = "title must be at most 255 characters"
    )
    private String title;

    private Boolean isDeleted;

    private Boolean isStarred;

    /**
     * The version the client last saw. Unlike the fields above this is required -
     * an update with no version could not be checked for conflicts.
     */
    @NotNull(message = "version is required")
    private Long version;
}
