package com.schema.versioncontrol.repository;

import com.schema.versioncontrol.model.Branch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findByRepositoryId(UUID repositoryId);
    Page<Branch> findByRepositoryIdOrderByCreatedAtDesc(UUID repositoryId, Pageable pageable);
    Optional<Branch> findByRepositoryIdAndName(UUID repositoryId, String name);
    boolean existsByRepositoryIdAndName(UUID repositoryId, String name);
}
