package com.schema.versioncontrol.repository;

import com.schema.versioncontrol.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
    List<AuditEvent> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<AuditEvent> findAllByOrderByCreatedAtDesc();
}
