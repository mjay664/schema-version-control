package com.schema.versioncontrol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchDto {
    private UUID id;
    private UUID repositoryId;
    private String name;
    private String sourceBranchName;
    private UUID headVersionId;
    private UserDto createdBy;
    private Instant createdAt;
}
