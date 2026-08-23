package com.schema.versioncontrol.controller;

import com.schema.versioncontrol.dto.*;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.service.SchemaVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class RepositoryController {

    private final SchemaVersionService schemaVersionService;

    @PostMapping
    public ResponseEntity<RepositoryDto> createRepository(
            @Valid @RequestBody CreateRepoRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        log.info("POST /api/repositories — name='{}', engine='{}', user='{}'", request.getName(), request.getDbEngine(), currentUser.getEmail());
        RepositoryDto repo = schemaVersionService.createRepository(request, currentUser);
        return ResponseEntity.ok(repo);
    }

    @GetMapping
    public ResponseEntity<List<RepositoryDto>> getAllRepositories(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(schemaVersionService.getAllRepositories(page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RepositoryDto> getRepository(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(schemaVersionService.getRepository(id));
    }

    @GetMapping("/{id}/branches")
    public ResponseEntity<List<BranchDto>> getBranches(
            @PathVariable("id") UUID id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(schemaVersionService.getBranches(id, page, size));
    }

    @PostMapping("/{id}/branches")
    public ResponseEntity<BranchDto> createBranch(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CreateBranchRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        log.info("POST /api/repositories/{}/branches — name='{}', source='{}', user='{}'", id, request.getName(), request.getSourceBranch(), currentUser.getEmail());
        BranchDto branch = schemaVersionService.createBranch(id, request, currentUser);
        return ResponseEntity.ok(branch);
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<SchemaVersionDto> commitVersion(
            @PathVariable("id") UUID id,
            @Valid @RequestBody CommitSchemaRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        log.info("POST /api/repositories/{}/versions — branch='{}', user='{}'", id, request.getBranchName(), currentUser.getEmail());
        SchemaVersionDto version = schemaVersionService.commitSchema(id, request, currentUser);
        return ResponseEntity.ok(version);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<SchemaVersionDto>> getVersionHistory(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(schemaVersionService.getVersionHistory(id));
    }

    @GetMapping("/{id}/diff")
    public ResponseEntity<DiffResultDto> getDiff(
            @PathVariable("id") UUID id,
            @RequestParam("sourceBranch") String sourceBranch,
            @RequestParam("targetBranch") String targetBranch
    ) {
        log.debug("GET /api/repositories/{}/diff — source='{}', target='{}'", id, sourceBranch, targetBranch);
        return ResponseEntity.ok(schemaVersionService.computeDiff(id, sourceBranch, targetBranch));
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<MergeResultDto> mergeBranches(
            @PathVariable("id") UUID id,
            @Valid @RequestBody MergeBranchRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        log.info("POST /api/repositories/{}/merge — source='{}', target='{}', user='{}'", id, request.getSourceBranch(), request.getTargetBranch(), currentUser.getEmail());
        MergeResultDto result = schemaVersionService.merge(id, request, currentUser);
        return ResponseEntity.ok(result);
    }
}
