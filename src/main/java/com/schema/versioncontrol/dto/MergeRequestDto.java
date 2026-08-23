package com.schema.versioncontrol.dto;

import com.schema.versioncontrol.constants.MergeRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MergeRequestDto {
    private UUID id;
    private UUID repositoryId;
    private BranchDto sourceBranch;
    private BranchDto targetBranch;
    private UUID sourceHeadVersionId;
    private UUID targetHeadVersionId;
    private MergeRequestStatus status;
    private UserDto createdBy;
    private UserDto requestedApprover;
    private Instant createdAt;
    private UserDto mergedBy;
    private Instant mergedAt;
    private List<MergeRequestApprovalDto> approvals;
    private boolean canApprove;
    private boolean canMerge;
    private DiffResultDto diff;
}
