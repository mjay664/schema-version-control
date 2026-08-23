package com.schema.versioncontrol.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitSchemaRequest {

    @NotBlank(message = "Branch name is required")
    private String branchName;

    @NotBlank(message = "Schema data payload is required")
    private String schemaData;

    private String commitMessage;
}
