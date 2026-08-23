package com.schema.versioncontrol.repository;

import com.schema.versioncontrol.model.RepositoryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryRepository extends JpaRepository<RepositoryEntity, UUID> {
    Optional<RepositoryEntity> findByName(String name);
    boolean existsByName(String name);
    Page<RepositoryEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
