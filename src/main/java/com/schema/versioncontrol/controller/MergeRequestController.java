package com.schema.versioncontrol.controller;

import com.schema.versioncontrol.dto.CreateMergeRequestRequest;
import com.schema.versioncontrol.dto.MergeRequestDto;
import com.schema.versioncontrol.dto.MergeResultDto;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.service.MergeRequestService;
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
@RequestMapping("/api")
@RequiredArgsConstructor
public class MergeRequestController {

    private final MergeRequestService mergeRequestService;

    @PostMapping("/merge-requests")
    public ResponseEntity<MergeRequestDto> createMergeRequest(
            @Valid @RequestBody CreateMergeRequestRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        log.info("POST /api/merge-requests — source={}, target={}, user='{}'", request.getSourceBranchId(), request.getTargetBranchId(), currentUser.getEmail());
        MergeRequestDto mr = mergeRequestService.createMergeRequest(request, currentUser);
        return ResponseEntity.ok(mr);
    }

    @GetMapping("/repositories/{repositoryId}/merge-requests")
    public ResponseEntity<List<MergeRequestDto>> getMergeRequestsForRepo(
            @PathVariable("repositoryId") UUID repositoryId,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(mergeRequestService.getMergeRequestsForRepo(repositoryId, currentUser));
    }

    @GetMapping("/merge-requests/{id}")
    public ResponseEntity<MergeRequestDto> getMergeRequestDetails(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(mergeRequestService.getMergeRequestDetails(id, currentUser));
    }

    @PostMapping("/merge-requests/{id}/approve")
    public ResponseEntity<MergeRequestDto> approveMergeRequest(
            @PathVariable("id") UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        log.info("POST /api/merge-requests/{}/approve — user='{}'", id, currentUser.getEmail());
        MergeRequestDto mr = mergeRequestService.approveMergeRequest(id, currentUser);
        return ResponseEntity.ok(mr);
    }

    @PostMapping("/merge-requests/{id}/merge")
    public ResponseEntity<MergeResultDto> mergeMergeRequest(
            @PathVariable("id") UUID id,
            @RequestBody(required = false) com.schema.versioncontrol.dto.ResolveConflictsRequest body,
            @AuthenticationPrincipal User currentUser
    ) {
        java.util.Map<String, String> resolutions = body != null ? body.getResolutions() : null;
        log.info("POST /api/merge-requests/{}/merge — user='{}', resolutions={}",
                id, currentUser.getEmail(), resolutions != null ? resolutions.size() : 0);
        MergeResultDto result = mergeRequestService.mergeMergeRequest(id, resolutions, currentUser);
        return ResponseEntity.ok(result);
    }
}
