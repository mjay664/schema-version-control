package com.schema.versioncontrol.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branches", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"repository_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(nullable = false)
    private String name;

    @Column(name = "source_branch_name")
    private String sourceBranchName;

    @Column(name = "head_version_id")
    private UUID headVersionId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Branch(UUID repositoryId, String name, String sourceBranchName, UUID headVersionId, User createdBy) {
        this.repositoryId = repositoryId;
        this.name = name;
        this.sourceBranchName = sourceBranchName;
        this.headVersionId = headVersionId;
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
