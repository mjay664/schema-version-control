package com.schema.versioncontrol.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merge_request_approvals", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"merge_request_id", "user_id", "source_head_version_id", "target_head_version_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class MergeRequestApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "merge_request_id", nullable = false)
    private MergeRequest mergeRequest;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "source_head_version_id", nullable = false)
    private UUID sourceHeadVersionId;

    @Column(name = "target_head_version_id", nullable = false)
    private UUID targetHeadVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MergeRequestApproval(MergeRequest mergeRequest, User user, UUID sourceHeadVersionId, UUID targetHeadVersionId) {
        this.mergeRequest = mergeRequest;
        this.user = user;
        this.sourceHeadVersionId = sourceHeadVersionId;
        this.targetHeadVersionId = targetHeadVersionId;
        this.createdAt = Instant.now();
    }
}
