package com.schema.versioncontrol.model;

import com.schema.versioncontrol.constants.MergeRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merge_requests")
@Getter
@Setter
@NoArgsConstructor
public class MergeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_branch_id", nullable = false)
    private Branch sourceBranch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_branch_id", nullable = false)
    private Branch targetBranch;

    @Column(name = "source_head_version_id")
    private UUID sourceHeadVersionId;

    @Column(name = "target_head_version_id")
    private UUID targetHeadVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MergeRequestStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requested_approver_id")
    private User requestedApprover;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "merged_by")
    private User mergedBy;

    @Column(name = "merged_at")
    private Instant mergedAt;

    public MergeRequest(UUID repositoryId, Branch sourceBranch, Branch targetBranch, UUID sourceHeadVersionId, UUID targetHeadVersionId, User createdBy, User requestedApprover) {
        this.repositoryId = repositoryId;
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.sourceHeadVersionId = sourceHeadVersionId;
        this.targetHeadVersionId = targetHeadVersionId;
        this.status = MergeRequestStatus.OPEN;
        this.createdBy = createdBy;
        this.requestedApprover = requestedApprover;
        this.createdAt = Instant.now();
    }
}
