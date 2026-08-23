package com.schema.versioncontrol.service;

import com.schema.versioncontrol.dto.AuditEventDto;
import com.schema.versioncontrol.model.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AuditService {
    AuditEvent recordEvent(UUID repositoryId, UUID userId, String actionType, String entityType, String entityId, String metadata);
    List<AuditEventDto> getAuditTrailForRepository(UUID repositoryId);
    List<AuditEventDto> getAllAuditEvents();
}
