package com.datanest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class CreateFileRequest {

    @NotBlank
    private String title;

    @NotBlank
    private String mimeType;

    @NotNull
    @Positive
    private Long size;

    @NotNull
    private Boolean isDeleted;

    @NotNull
    private Boolean isStarred;
}

