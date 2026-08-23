package com.schema.versioncontrol.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMergeRequestRequest {

    @NotNull(message = "Repository ID is required")
    private UUID repositoryId;

    @NotNull(message = "Source branch ID is required")
    private UUID sourceBranchId;

    @NotNull(message = "Target branch ID is required")
    private UUID targetBranchId;

    private UUID requestedApproverId;

    public CreateMergeRequestRequest(UUID repositoryId, UUID sourceBranchId, UUID targetBranchId) {
        this.repositoryId = repositoryId;
        this.sourceBranchId = sourceBranchId;
        this.targetBranchId = targetBranchId;
    }
}
