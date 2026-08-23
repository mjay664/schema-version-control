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
public class AuditEventDto {
    private UUID id;
    private UUID repositoryId;
    private UUID userId;
    private String userDisplayName;
    private String userEmail;
    private String actionType;
    private String entityType;
    private String entityId;
    private String metadata;
    private Instant createdAt;
}
