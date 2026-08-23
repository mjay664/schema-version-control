package com.schema.versioncontrol.dto;

import com.schema.versioncontrol.constants.DatabaseEngine;
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
public class CreateRepoRequest {

    @NotBlank(message = "Repository name is required")
    @Pattern(regexp = "^[a-zA-Z0-9_.-]+$", message = "Repository name can only contain letters, numbers, underscores, dots, and hyphens")
    private String name;

    private DatabaseEngine dbEngine;
}
