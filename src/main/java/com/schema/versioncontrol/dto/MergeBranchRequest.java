package com.schema.versioncontrol.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MergeBranchRequest {

    @NotBlank(message = "Source branch is required")
    private String sourceBranch;

    @NotBlank(message = "Target branch is required")
    private String targetBranch;

    private String resolvedSchemaData;

    /**
     * Per-conflict decisions, keyed by "table" or "table.column", valued
     * "TARGET" or "SOURCE". Lets a caller settle conflicts by choosing a side
     * rather than posting a whole schema, so a merge can never introduce a
     * definition that is in neither branch.
     */
    private Map<String, String> conflictResolutions;

    public MergeBranchRequest(String sourceBranch, String targetBranch, String resolvedSchemaData) {
        this.sourceBranch = sourceBranch;
        this.targetBranch = targetBranch;
        this.resolvedSchemaData = resolvedSchemaData;
    }
}
