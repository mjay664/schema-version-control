package com.schema.versioncontrol.repository;

import com.schema.versioncontrol.model.SchemaVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SchemaVersionRepository extends JpaRepository<SchemaVersion, UUID> {
    List<SchemaVersion> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId);
}
