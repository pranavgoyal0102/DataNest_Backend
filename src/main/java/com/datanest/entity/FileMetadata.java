package com.datanest.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.UUID;

@Entity
@Table(
        name = "files",
        indexes = {
                @Index(name = "idx_uid", columnList = "firebaseUid"),
                @Index(name = "idx_deleted", columnList = "isDeleted"),
                @Index(name = "idx_updated", columnList = "updatedAt"),
                @Index(name = "idx_sync", columnList = "firebaseUid, updatedAt, id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private String firebaseUid;

    @NotBlank
    private String title;

    private String mimeType;

    private Long size;

    private String cloudUrl;

    /**
     * Cloudinary handles. Both are needed to delete the asset; nullable because createFile
     * records metadata with no upload behind it. Not exposed on FileResponse - they are
     * storage internals and publicId would let a client address the asset directly.
     */
    private String publicId;

    private String resourceType;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isStarred = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean isDeleted = false;

    private Long createdAt;

    private Long updatedAt;

    /**
     * Owned by Hibernate: set to 0 on insert and incremented on every update.
     * Deliberately has no @Builder.Default - a builder default would fight that.
     */
    @Version
    @Column(nullable = false)
    private Long version;
}