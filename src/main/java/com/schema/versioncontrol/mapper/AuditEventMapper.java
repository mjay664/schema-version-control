package com.schema.versioncontrol.mapper;

import com.schema.versioncontrol.dto.AuditEventDto;
import com.schema.versioncontrol.model.AuditEvent;
import com.schema.versioncontrol.model.User;
import org.springframework.stereotype.Component;

@Component
public class AuditEventMapper {

    public AuditEventDto toDto(AuditEvent event, User user) {
        if (event == null) return null;
        String displayName = user != null ? user.getDisplayName() : "Unknown User";
        String email = user != null ? user.getEmail() : "";
        return new AuditEventDto(
                event.getId(),
                event.getRepositoryId(),
                event.getUserId(),
                displayName,
                email,
                event.getActionType(),
                event.getEntityType(),
                event.getEntityId(),
                event.getMetadata(),
                event.getCreatedAt()
        );
    }
}
