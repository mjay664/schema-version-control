package com.schema.versioncontrol.repository;

import com.schema.versioncontrol.model.MergeRequestApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface MergeRequestApprovalRepository extends JpaRepository<MergeRequestApproval, UUID> {
    List<MergeRequestApproval> findByMergeRequestId(UUID mergeRequestId);
    boolean existsByMergeRequestIdAndUserIdAndSourceHeadVersionIdAndTargetHeadVersionId(UUID mergeRequestId, UUID userId, UUID sourceHeadVersionId, UUID targetHeadVersionId);
}
