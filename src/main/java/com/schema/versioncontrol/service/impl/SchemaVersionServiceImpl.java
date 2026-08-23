package com.schema.versioncontrol.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schema.versioncontrol.constants.AuditConstants;
import com.schema.versioncontrol.constants.DatabaseEngine;
import com.schema.versioncontrol.dto.*;
import com.schema.versioncontrol.exception.DuplicateResourceException;
import com.schema.versioncontrol.exception.InvalidSchemaException;
import com.schema.versioncontrol.exception.ResourceNotFoundException;
import com.schema.versioncontrol.mapper.BranchMapper;
import com.schema.versioncontrol.mapper.RepositoryMapper;
import com.schema.versioncontrol.mapper.SchemaVersionMapper;
import com.schema.versioncontrol.mapper.UserMapper;
import com.schema.versioncontrol.model.*;
import com.schema.versioncontrol.repository.BranchRepository;
import com.schema.versioncontrol.repository.RepositoryRepository;
import com.schema.versioncontrol.repository.SchemaVersionRepository;
import com.schema.versioncontrol.service.AuditService;
import com.schema.versioncontrol.service.DatabaseTypeService;
import com.schema.versioncontrol.service.SchemaVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaVersionServiceImpl implements SchemaVersionService {

    private final RepositoryRepository repositoryRepository;
    private final BranchRepository branchRepository;
    private final SchemaVersionRepository schemaVersionRepository;
    private final AuditService auditService;
    private final DatabaseTypeService databaseTypeService;
    private final RepositoryMapper repositoryMapper;
    private final BranchMapper branchMapper;
    private final SchemaVersionMapper schemaVersionMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public RepositoryDto createRepository(CreateRepoRequest request, User actor) {
        log.info("Creating repository '{}' with engine '{}' by user '{}'", request.getName(), request.getDbEngine(), actor.getEmail());
        if (repositoryRepository.existsByName(request.getName())) {
            log.warn("Duplicate repository name attempted: '{}'", request.getName());
            throw new DuplicateResourceException("Repository with name '" + request.getName() + "' already exists");
        }

        DatabaseEngine engine = request.getDbEngine() != null ? request.getDbEngine() : DatabaseEngine.POSTGRESQL;

        // 1. Save Repository
        RepositoryEntity repo = new RepositoryEntity(request.getName(), engine, actor);
        repo = repositoryRepository.save(repo);

        // 2. Initial empty schema version
        String initialSchema = "{\"tables\":[]}";
        SchemaVersion initialVersion = new SchemaVersion(repo.getId(), initialSchema, null, "Initial commit", actor);
        initialVersion = schemaVersionRepository.save(initialVersion);

        // 3. Create default branch 'main'
        Branch mainBranch = new Branch(repo.getId(), "main", null, initialVersion.getId(), actor);
        mainBranch = branchRepository.save(mainBranch);

        // 4. Record transactional audit events
        auditService.recordEvent(repo.getId(), actor.getId(), AuditConstants.ACTION_REPOSITORY_CREATED, AuditConstants.ENTITY_REPOSITORY, repo.getId().toString(),
                "{\"name\":\"" + repo.getName() + "\",\"dbEngine\":\"" + engine.name() + "\"}");
        auditService.recordEvent(repo.getId(), actor.getId(), AuditConstants.ACTION_BRANCH_CREATED, AuditConstants.ENTITY_BRANCH, mainBranch.getId().toString(),
                "{\"name\":\"main\"}");

        log.info("Repository '{}' created with id={}", repo.getName(), repo.getId());
        return repositoryMapper.toDto(repo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RepositoryDto> getAllRepositories(int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size));
        return repositoryRepository.findAllByOrderByCreatedAtDesc(pageable).stream()
                .map(repositoryMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RepositoryDto getRepository(UUID repoId) {
        RepositoryEntity repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repoId));
        return repositoryMapper.toDto(repo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchDto> getBranches(UUID repoId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(Math.max(0, page), Math.max(1, size));
        return branchRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId, pageable).stream()
                .map(branchMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public BranchDto createBranch(UUID repoId, CreateBranchRequest request, User actor) {
        log.info("Creating branch '{}' in repo={} from source='{}' by user '{}'", request.getName(), repoId, request.getSourceBranch(), actor.getEmail());
        if (branchRepository.existsByRepositoryIdAndName(repoId, request.getName())) {
            log.warn("Duplicate branch name '{}' in repo={}", request.getName(), repoId);
            throw new DuplicateResourceException("Branch '" + request.getName() + "' already exists in repository");
        }

        String sourceBranchName = request.getSourceBranch() != null ? request.getSourceBranch() : "main";
        Branch source = branchRepository.findByRepositoryIdAndName(repoId, sourceBranchName)
                .orElseThrow(() -> new ResourceNotFoundException("Source branch '" + sourceBranchName + "' not found"));

        Branch branch = new Branch(repoId, request.getName(), source.getName(), source.getHeadVersionId(), actor);
        branch = branchRepository.save(branch);

        auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_BRANCH_CREATED, AuditConstants.ENTITY_BRANCH, branch.getId().toString(),
                "{\"name\":\"" + request.getName() + "\",\"sourceBranch\":\"" + source.getName() + "\"}");

        return branchMapper.toDto(branch);
    }

    @Override
    @Transactional
    public SchemaVersionDto commitSchema(UUID repoId, CommitSchemaRequest request, User actor) {
        log.info("Committing schema to branch '{}' in repo={} by user '{}'", request.getBranchName(), repoId, actor.getEmail());
        RepositoryEntity repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repoId));

        Branch branch = branchRepository.findByRepositoryIdAndName(repoId, request.getBranchName())
                .orElseThrow(() -> new ResourceNotFoundException("Branch '" + request.getBranchName() + "' not found"));

        if ("main".equalsIgnoreCase(request.getBranchName())) {
            log.warn("Blocked direct commit to 'main' branch by user '{}'", actor.getEmail());
            throw new InvalidSchemaException("Direct commits to the 'main' branch are restricted. Changes must be committed on a feature branch and merged via an approved Merge Request.");
        }

        // Validate Schema and Data Types
        databaseTypeService.validateSchemaDataTypes(request.getSchemaData(), repo.getDbEngine());

        UUID parentId = branch.getHeadVersionId();
        String parentSchemaJson = "{\"tables\":[]}";
        if (parentId != null) {
            SchemaVersion parentVersion = schemaVersionRepository.findById(parentId).orElse(null);
            if (parentVersion != null) {
                parentSchemaJson = parentVersion.getSchemaData();
            }
        }

        // Save new version
        SchemaVersion version = new SchemaVersion(
                repoId,
                request.getSchemaData(),
                parentId != null ? parentId.toString() : null,
                request.getCommitMessage() != null ? request.getCommitMessage() : "Update schema",
                actor
        );
        version = schemaVersionRepository.save(version);

        // Update branch head
        branch.setHeadVersionId(version.getId());
        branchRepository.save(branch);

        // Analyze differences and emit audit events
        analyzeAndRecordAuditEvents(repoId, actor, parentSchemaJson, request.getSchemaData());

        return schemaVersionMapper.toDto(version);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SchemaVersionDto> getVersionHistory(UUID repoId) {
        return schemaVersionRepository.findByRepositoryIdOrderByCreatedAtDesc(repoId).stream()
                .map(schemaVersionMapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public DiffResultDto computeDiff(UUID repoId, String sourceBranchName, String targetBranchName) {
        Branch source = branchRepository.findByRepositoryIdAndName(repoId, sourceBranchName)
                .orElseThrow(() -> new ResourceNotFoundException("Source branch '" + sourceBranchName + "' not found"));
        Branch target = branchRepository.findByRepositoryIdAndName(repoId, targetBranchName)
                .orElseThrow(() -> new ResourceNotFoundException("Target branch '" + targetBranchName + "' not found"));

        SchemaVersion sourceHead = source.getHeadVersionId() != null ? schemaVersionRepository.findById(source.getHeadVersionId()).orElse(null) : null;
        SchemaVersion targetHead = target.getHeadVersionId() != null ? schemaVersionRepository.findById(target.getHeadVersionId()).orElse(null) : null;

        UserDto sourceHeadUser = sourceHead != null ? userMapper.toDto(sourceHead.getCreatedBy()) : null;
        UserDto targetHeadUser = targetHead != null ? userMapper.toDto(targetHead.getCreatedBy()) : null;

        String sourceSchema = sourceHead != null ? sourceHead.getSchemaData() : "{\"tables\":[]}";
        String targetSchema = targetHead != null ? targetHead.getSchemaData() : "{\"tables\":[]}";

        String ancestorId = findCommonAncestorId(sourceHead, targetHead);

        return calculateSchemaDiff(sourceBranchName, targetBranchName, sourceHeadUser, targetHeadUser, ancestorId, sourceSchema, targetSchema);
    }

    @Override
    @Transactional
    public MergeResultDto merge(UUID repoId, MergeBranchRequest request, User actor) {
        log.info("Merging branch '{}' into '{}' in repo={} by user '{}'", request.getSourceBranch(), request.getTargetBranch(), repoId, actor.getEmail());
        RepositoryEntity repo = repositoryRepository.findById(repoId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repoId));

        Branch source = branchRepository.findByRepositoryIdAndName(repoId, request.getSourceBranch())
                .orElseThrow(() -> new ResourceNotFoundException("Source branch '" + request.getSourceBranch() + "' not found"));
        Branch target = branchRepository.findByRepositoryIdAndName(repoId, request.getTargetBranch())
                .orElseThrow(() -> new ResourceNotFoundException("Target branch '" + request.getTargetBranch() + "' not found"));

        auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_MERGE_STARTED, AuditConstants.ENTITY_MERGE, null,
                "{\"sourceBranch\":\"" + request.getSourceBranch() + "\",\"targetBranch\":\"" + request.getTargetBranch() + "\"}");

        SchemaVersion sourceHead = source.getHeadVersionId() != null ? schemaVersionRepository.findById(source.getHeadVersionId()).orElse(null) : null;
        SchemaVersion targetHead = target.getHeadVersionId() != null ? schemaVersionRepository.findById(target.getHeadVersionId()).orElse(null) : null;

        String sourceSchema = sourceHead != null ? sourceHead.getSchemaData() : "{\"tables\":[]}";
        String targetSchema = targetHead != null ? targetHead.getSchemaData() : "{\"tables\":[]}";

        String ancestorSchema = findCommonAncestorSchema(sourceHead, targetHead);

        java.util.Map<String, String> resolutions = request.getConflictResolutions() != null
                ? request.getConflictResolutions()
                : java.util.Map.of();

        // Compute twice when resolutions are supplied: once to learn what the
        // merge conflicts on at all, once with the decisions applied. The first
        // pass is what tells us a resolution was genuinely needed rather than
        // smuggled in against a clean merge.
        ThreeWayMergeEngine.MergeCalculation unresolved =
                ThreeWayMergeEngine.compute(ancestorSchema, targetSchema, sourceSchema);
        ThreeWayMergeEngine.MergeCalculation calc = resolutions.isEmpty()
                ? unresolved
                : ThreeWayMergeEngine.compute(ancestorSchema, targetSchema, sourceSchema, resolutions);

        if (calc.isHasConflicts()) {
            if (request.getResolvedSchemaData() == null || request.getResolvedSchemaData().isBlank()) {
                auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_MERGE_CONFLICT_DETECTED, AuditConstants.ENTITY_MERGE, null,
                        "{\"conflictsCount\":" + calc.getConflicts().size() + "}");
                log.warn("Merge conflicts detected: {} conflicts between '{}' and '{}'", calc.getConflicts().size(), request.getSourceBranch(), request.getTargetBranch());

                return new MergeResultDto(false, true, calc.getMergedSchemaJson(), null, calc.getConflicts());
            }
        }

        String finalSchema = (request.getResolvedSchemaData() != null && !request.getResolvedSchemaData().isBlank())
                ? request.getResolvedSchemaData()
                : calc.getMergedSchemaJson();

        // Validate merged schema data types
        databaseTypeService.validateSchemaDataTypes(finalSchema, repo.getDbEngine());

        String parentIds = (targetHead != null ? targetHead.getId().toString() : "") +
                "," + (sourceHead != null ? sourceHead.getId().toString() : "");

        SchemaVersion mergeVersion = new SchemaVersion(
                repoId,
                finalSchema,
                parentIds,
                "Merge branch '" + request.getSourceBranch() + "' into '" + request.getTargetBranch() + "'",
                actor
        );
        mergeVersion = schemaVersionRepository.save(mergeVersion);

        target.setHeadVersionId(mergeVersion.getId());
        branchRepository.save(target);

        boolean resolvedConflicts = unresolved.isHasConflicts()
                && (!resolutions.isEmpty() || request.getResolvedSchemaData() != null);
        if (resolvedConflicts) {
            auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_MERGE_CONFLICT_RESOLVED, AuditConstants.ENTITY_MERGE, mergeVersion.getId().toString(),
                    "{\"sourceBranch\":\"" + request.getSourceBranch() + "\",\"targetBranch\":\"" + request.getTargetBranch()
                            + "\",\"resolutions\":" + toJsonOrNull(resolutions) + "}");
        } else {
            auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_MERGE_COMPLETED, AuditConstants.ENTITY_MERGE, mergeVersion.getId().toString(),
                    "{\"sourceBranch\":\"" + request.getSourceBranch() + "\",\"targetBranch\":\"" + request.getTargetBranch() + "\"}");
        }

        return new MergeResultDto(true, false, finalSchema, schemaVersionMapper.toDto(mergeVersion), Collections.emptyList());
    }

    /** Serialise a small map for an audit payload; never throws into the merge path. */
    private String toJsonOrNull(java.util.Map<String, String> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "null";
        }
    }

    /**
     * Id of the version {@link #findCommonAncestorSchema} would resolve to.
     * Exposed on the diff so a reviewer's client can label each side's change
     * relative to the point the branches diverged, rather than only against
     * each other.
     */
    private String findCommonAncestorId(SchemaVersion v1, SchemaVersion v2) {
        if (v1 == null || v2 == null) return null;
        if (v1.getId().equals(v2.getId())) return v1.getId().toString();

        Set<UUID> v1Ancestors = getAncestors(v1);
        if (v1Ancestors.contains(v2.getId())) return v2.getId().toString();

        Set<UUID> v2Ancestors = getAncestors(v2);
        if (v2Ancestors.contains(v1.getId())) return v1.getId().toString();

        for (UUID id : v1Ancestors) {
            if (v2Ancestors.contains(id)) return id.toString();
        }
        return null;
    }

    private String findCommonAncestorSchema(SchemaVersion v1, SchemaVersion v2) {
        if (v1 == null || v2 == null) return "{\"tables\":[]}";
        if (v1.getId().equals(v2.getId())) return v1.getSchemaData();

        Set<UUID> v1Ancestors = getAncestors(v1);
        if (v1Ancestors.contains(v2.getId())) return v2.getSchemaData();

        Set<UUID> v2Ancestors = getAncestors(v2);
        if (v2Ancestors.contains(v1.getId())) return v1.getSchemaData();

        for (UUID id : v1Ancestors) {
            if (v2Ancestors.contains(id)) {
                return schemaVersionRepository.findById(id).map(SchemaVersion::getSchemaData).orElse("{\"tables\":[]}");
            }
        }
        return "{\"tables\":[]}";
    }

    private Set<UUID> getAncestors(SchemaVersion version) {
        Set<UUID> ancestors = new LinkedHashSet<>();
        Queue<SchemaVersion> queue = new LinkedList<>();
        queue.add(version);

        while (!queue.isEmpty()) {
            SchemaVersion current = queue.poll();
            if (current.getParentVersionIds() != null && !current.getParentVersionIds().isBlank()) {
                String[] parents = current.getParentVersionIds().split(",");
                for (String p : parents) {
                    if (!p.trim().isEmpty()) {
                        try {
                            UUID parentUuid = UUID.fromString(p.trim());
                            if (ancestors.add(parentUuid)) {
                                schemaVersionRepository.findById(parentUuid).ifPresent(queue::add);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return ancestors;
    }

    private void analyzeAndRecordAuditEvents(UUID repoId, User actor, String oldSchemaJson, String newSchemaJson) {
        try {
            Map<String, Map<String, Object>> oldTableMap = toTableMap(getTables(parseSchema(oldSchemaJson)));
            Map<String, Map<String, Object>> newTableMap = toTableMap(getTables(parseSchema(newSchemaJson)));

            for (String tableName : newTableMap.keySet()) {
                if (!oldTableMap.containsKey(tableName)) {
                    auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_TABLE_CREATED, AuditConstants.ENTITY_TABLE, tableName, "{\"tableName\":\"" + tableName + "\"}");
                } else {
                    analyzeColumns(repoId, actor, tableName, getColumns(oldTableMap.get(tableName)), getColumns(newTableMap.get(tableName)));
                }
            }

            for (String tableName : oldTableMap.keySet()) {
                if (!newTableMap.containsKey(tableName)) {
                    auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_TABLE_DROPPED, AuditConstants.ENTITY_TABLE, tableName, "{\"tableName\":\"" + tableName + "\"}");
                }
            }
        } catch (Exception e) {
            auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_SCHEMA_UPDATED, AuditConstants.ENTITY_SCHEMA, null, "{}");
        }
    }

    private void analyzeColumns(UUID repoId, User actor, String tableName, List<Map<String, Object>> oldCols, List<Map<String, Object>> newCols) {
        Map<String, Map<String, Object>> oldColMap = toColMap(oldCols);
        Map<String, Map<String, Object>> newColMap = toColMap(newCols);

        for (String colName : newColMap.keySet()) {
            if (!oldColMap.containsKey(colName)) {
                Map<String, Object> col = newColMap.get(colName);
                auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_COLUMN_CREATED, AuditConstants.ENTITY_COLUMN, tableName + "." + colName,
                        "{\"tableName\":\"" + tableName + "\",\"columnName\":\"" + colName + "\",\"type\":\"" + col.get("type") + "\"}");
            } else {
                Map<String, Object> oldCol = oldColMap.get(colName);
                Map<String, Object> newCol = newColMap.get(colName);
                String oldType = String.valueOf(oldCol.get("type"));
                String newType = String.valueOf(newCol.get("type"));
                if (!oldType.equals(newType)) {
                    auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_COLUMN_MODIFIED, AuditConstants.ENTITY_COLUMN, tableName + "." + colName,
                            "{\"tableName\":\"" + tableName + "\",\"columnName\":\"" + colName + "\",\"from\":\"" + oldType + "\",\"to\":\"" + newType + "\"}");
                }
            }
        }

        for (String colName : oldColMap.keySet()) {
            if (!newColMap.containsKey(colName)) {
                auditService.recordEvent(repoId, actor.getId(), AuditConstants.ACTION_COLUMN_DROPPED, AuditConstants.ENTITY_COLUMN, tableName + "." + colName,
                        "{\"tableName\":\"" + tableName + "\",\"columnName\":\"" + colName + "\"}");
            }
        }
    }

    private DiffResultDto calculateSchemaDiff(String sourceBranch, String targetBranch, UserDto sourceHeadUser, UserDto targetHeadUser, String ancestorId, String sourceJson, String targetJson) {
        List<String> addedTables = new ArrayList<>();
        List<String> removedTables = new ArrayList<>();
        List<String> modifiedTables = new ArrayList<>();
        List<Map<String, Object>> detailed = new ArrayList<>();

        try {
            Map<String, Map<String, Object>> sourceMap = toTableMap(getTables(parseSchema(sourceJson)));
            Map<String, Map<String, Object>> targetMap = toTableMap(getTables(parseSchema(targetJson)));

            for (String t : sourceMap.keySet()) {
                if (!targetMap.containsKey(t)) {
                    addedTables.add(t);
                    detailed.add(Map.of("action", "ADD_TABLE", "table", t));
                } else {
                    List<Map<String, Object>> srcCols = getColumns(sourceMap.get(t));
                    List<Map<String, Object>> tgtCols = getColumns(targetMap.get(t));
                    if (!srcCols.equals(tgtCols)) {
                        modifiedTables.add(t);
                        detailed.add(Map.of("action", "MODIFY_TABLE", "table", t, "sourceColumns", srcCols, "targetColumns", tgtCols));
                    }
                }
            }
            for (String t : targetMap.keySet()) {
                if (!sourceMap.containsKey(t)) {
                    removedTables.add(t);
                    detailed.add(Map.of("action", "REMOVE_TABLE", "table", t));
                }
            }
        } catch (Exception ignored) {}

        return new DiffResultDto(sourceBranch, targetBranch, sourceHeadUser, targetHeadUser, ancestorId, addedTables, removedTables, modifiedTables, detailed);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseSchema(String json) throws Exception {
        if (json == null || json.isBlank()) return Map.of("tables", List.of());
        return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getTables(Map<String, Object> schemaMap) {
        Object tables = schemaMap.get("tables");
        if (tables instanceof List) {
            return (List<Map<String, Object>>) tables;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getColumns(Map<String, Object> tableMap) {
        Object cols = tableMap.get("columns");
        if (cols instanceof List) {
            return (List<Map<String, Object>>) cols;
        }
        return List.of();
    }

    private Map<String, Map<String, Object>> toTableMap(List<Map<String, Object>> tables) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> t : tables) {
            Object name = t.get("name");
            if (name != null) {
                map.put(name.toString(), t);
            }
        }
        return map;
    }

    private Map<String, Map<String, Object>> toColMap(List<Map<String, Object>> cols) {
        Map<String, Map<String, Object>> map = new HashMap<>();
        for (Map<String, Object> c : cols) {
            Object name = c.get("name");
            if (name != null) {
                map.put(name.toString(), c);
            }
        }
        return map;
    }
}
