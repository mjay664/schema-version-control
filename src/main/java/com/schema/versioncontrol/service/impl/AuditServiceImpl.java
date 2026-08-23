package com.schema.versioncontrol.service.impl;

import com.schema.versioncontrol.dto.AuditEventDto;
import com.schema.versioncontrol.mapper.AuditEventMapper;
import com.schema.versioncontrol.model.AuditEvent;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.repository.AuditEventRepository;
import com.schema.versioncontrol.repository.UserRepository;
import com.schema.versioncontrol.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final AuditEventMapper auditEventMapper;

    @Override
    @Transactional
    public AuditEvent recordEvent(UUID repositoryId, UUID userId, String actionType, String entityType, String entityId, String metadata) {
        log.debug("Recording audit event: action={}, entity={}, entityId={}, userId={}", actionType, entityType, entityId, userId);
        AuditEvent event = new AuditEvent(repositoryId, userId, actionType, entityType, entityId, metadata);
        return auditEventRepository.save(event);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventDto> getAuditTrailForRepository(UUID repositoryId) {
        log.debug("Fetching audit trail for repository={}", repositoryId);
        List<AuditEvent> events = auditEventRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId);
        return enrichAuditEvents(events);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AuditEventDto> getAllAuditEvents() {
        List<AuditEvent> events = auditEventRepository.findAllByOrderByCreatedAtDesc();
        return enrichAuditEvents(events);
    }

    private List<AuditEventDto> enrichAuditEvents(List<AuditEvent> events) {
        List<UUID> userIds = events.stream().map(AuditEvent::getUserId).distinct().collect(Collectors.toList());
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return events.stream().map(e -> {
            User user = userMap.get(e.getUserId());
            return auditEventMapper.toDto(e, user);
        }).collect(Collectors.toList());
    }
}
