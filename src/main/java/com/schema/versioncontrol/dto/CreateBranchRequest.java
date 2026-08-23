package com.schema.versioncontrol.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateBranchRequest {

    @NotBlank(message = "Branch name is required")
    @Pattern(regexp = "^[a-zA-Z0-9/_.-]+$", message = "Branch name can only contain letters, numbers, slashes, underscores, dots, and hyphens")
    private String name;

    private String sourceBranch;
}
