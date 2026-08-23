package com.schema.versioncontrol.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schema_versions")
@Getter
@Setter
@NoArgsConstructor
public class SchemaVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "schema_data", columnDefinition = "TEXT", nullable = false)
    private String schemaData;

    @Column(name = "parent_version_ids")
    private String parentVersionIds;

    @Column(name = "commit_message")
    private String commitMessage;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public SchemaVersion(UUID repositoryId, String schemaData, String parentVersionIds, String commitMessage, User createdBy) {
        this.repositoryId = repositoryId;
        this.schemaData = schemaData;
        this.parentVersionIds = parentVersionIds;
        this.commitMessage = commitMessage;
        this.createdBy = createdBy;
        this.createdAt = Instant.now();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
