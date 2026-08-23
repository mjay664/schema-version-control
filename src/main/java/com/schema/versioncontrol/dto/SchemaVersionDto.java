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
public class SchemaVersionDto {
    private UUID id;
    private UUID repositoryId;
    private String schemaData;
    private String parentVersionIds;
    private String commitMessage;
    private UserDto createdBy;
    private Instant createdAt;
}
