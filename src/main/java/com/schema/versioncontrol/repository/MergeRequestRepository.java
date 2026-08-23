package com.schema.versioncontrol.repository;

import com.schema.versioncontrol.constants.MergeRequestStatus;
import com.schema.versioncontrol.model.MergeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MergeRequestRepository extends JpaRepository<MergeRequest, UUID> {
    List<MergeRequest> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
    Optional<MergeRequest> findByRepositoryIdAndSourceBranchIdAndTargetBranchIdAndStatus(UUID repositoryId, UUID sourceBranchId, UUID targetBranchId, MergeRequestStatus status);
}
