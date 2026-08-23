package com.schema.versioncontrol.service.impl;

import com.schema.versioncontrol.constants.AuditConstants;
import com.schema.versioncontrol.constants.MergeRequestStatus;
import com.schema.versioncontrol.dto.*;
import com.schema.versioncontrol.exception.InvalidSchemaException;
import com.schema.versioncontrol.exception.ResourceNotFoundException;
import com.schema.versioncontrol.mapper.BranchMapper;
import com.schema.versioncontrol.mapper.UserMapper;
import com.schema.versioncontrol.model.Branch;
import com.schema.versioncontrol.model.MergeRequestApproval;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.repository.BranchRepository;
import com.schema.versioncontrol.repository.MergeRequestApprovalRepository;
import com.schema.versioncontrol.repository.MergeRequestRepository;
import com.schema.versioncontrol.repository.UserRepository;
import com.schema.versioncontrol.service.AuditService;
import com.schema.versioncontrol.service.MergeRequestService;
import com.schema.versioncontrol.service.SchemaVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MergeRequestServiceImpl implements MergeRequestService {

    private final MergeRequestRepository mergeRequestRepository;
    private final MergeRequestApprovalRepository mergeRequestApprovalRepository;
    private final BranchRepository branchRepository;
    private final SchemaVersionService schemaVersionService;
    private final AuditService auditService;
    private final BranchMapper branchMapper;
    private final UserMapper userMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public MergeRequestDto createMergeRequest(CreateMergeRequestRequest request, User actor) {
        log.info("Creating merge request: source={}, target={}, repo={}, by user='{}'", request.getSourceBranchId(), request.getTargetBranchId(), request.getRepositoryId(), actor.getEmail());
        Branch sourceBranch = branchRepository.findById(request.getSourceBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Source branch not found"));
        Branch targetBranch = branchRepository.findById(request.getTargetBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Target branch not found"));

        if (!sourceBranch.getRepositoryId().equals(request.getRepositoryId()) ||
            !targetBranch.getRepositoryId().equals(request.getRepositoryId())) {
            throw new InvalidSchemaException("Branches do not belong to the specified repository");
        }

        if (sourceBranch.getId().equals(targetBranch.getId())) {
            throw new InvalidSchemaException("Source and target branches must be different");
        }

        Optional<com.schema.versioncontrol.model.MergeRequest> existingOpen = mergeRequestRepository.findByRepositoryIdAndSourceBranchIdAndTargetBranchIdAndStatus(
                request.getRepositoryId(), sourceBranch.getId(), targetBranch.getId(), MergeRequestStatus.OPEN);

        if (existingOpen.isPresent()) {
            return toDto(existingOpen.get(), actor);
        }

        User requestedApprover = null;
        if (request.getRequestedApproverId() != null) {
            requestedApprover = userRepository.findById(request.getRequestedApproverId()).orElse(null);
        }

        com.schema.versioncontrol.model.MergeRequest mr = new com.schema.versioncontrol.model.MergeRequest(
                request.getRepositoryId(),
                sourceBranch,
                targetBranch,
                sourceBranch.getHeadVersionId(),
                targetBranch.getHeadVersionId(),
                actor,
                requestedApprover
        );

        mr = mergeRequestRepository.save(mr);

        auditService.recordEvent(
                request.getRepositoryId(),
                actor.getId(),
                AuditConstants.ACTION_MERGE_REQUEST_CREATED,
                AuditConstants.ENTITY_MERGE_REQUEST,
                mr.getId().toString(),
                "{\"sourceBranch\":\"" + sourceBranch.getName() + "\",\"targetBranch\":\"" + targetBranch.getName() + "\"}"
        );

        return toDto(mr, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MergeRequestDto> getMergeRequestsForRepo(UUID repositoryId, User actor) {
        List<com.schema.versioncontrol.model.MergeRequest> mrs = mergeRequestRepository.findByRepositoryIdOrderByCreatedAtDesc(repositoryId);
        return mrs.stream().map(mr -> toDto(mr, actor)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MergeRequestDto getMergeRequestDetails(UUID mergeRequestId, User actor) {
        com.schema.versioncontrol.model.MergeRequest mr = mergeRequestRepository.findById(mergeRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Merge Request not found with ID: " + mergeRequestId));
        return toDto(mr, actor);
    }

    @Override
    @Transactional
    public MergeRequestDto approveMergeRequest(UUID mergeRequestId, User actor) {
        com.schema.versioncontrol.model.MergeRequest mr = mergeRequestRepository.findById(mergeRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Merge Request not found with ID: " + mergeRequestId));

        if (mr.getStatus() == MergeRequestStatus.MERGED) {
            throw new InvalidSchemaException("Cannot approve an already merged request");
        }

        if (mr.getCreatedBy().getId().equals(actor.getId())) {
            log.warn("User '{}' attempted to self-approve merge request id={}", actor.getEmail(), mergeRequestId);
            throw new AccessDeniedException("You cannot approve your own merge request");
        }

        UUID currentSourceHead = mr.getSourceBranch().getHeadVersionId();
        UUID currentTargetHead = mr.getTargetBranch().getHeadVersionId();

        if (currentSourceHead == null || currentTargetHead == null) {
            throw new InvalidSchemaException("Branches must have committed versions to approve");
        }

        if (!mergeRequestApprovalRepository.existsByMergeRequestIdAndUserIdAndSourceHeadVersionIdAndTargetHeadVersionId(
                mr.getId(), actor.getId(), currentSourceHead, currentTargetHead)) {
            
            MergeRequestApproval approval = new MergeRequestApproval(mr, actor, currentSourceHead, currentTargetHead);
            mergeRequestApprovalRepository.save(approval);

            mr.setStatus(MergeRequestStatus.APPROVED);
            mergeRequestRepository.save(mr);
            log.info("Merge request id={} approved by user '{}'", mr.getId(), actor.getEmail());

            auditService.recordEvent(
                    mr.getRepositoryId(),
                    actor.getId(),
                    AuditConstants.ACTION_MERGE_REQUEST_APPROVED,
                    AuditConstants.ENTITY_MERGE_REQUEST,
                    mr.getId().toString(),
                    "{\"sourceHead\":\"" + currentSourceHead + "\",\"targetHead\":\"" + currentTargetHead + "\"}"
            );
        }

        return toDto(mr, actor);
    }

    @Override
    @Transactional
    public MergeResultDto mergeMergeRequest(UUID mergeRequestId, java.util.Map<String, String> conflictResolutions, User actor) {
        log.info("Executing merge for merge request id={} by user '{}'", mergeRequestId, actor.getEmail());
        com.schema.versioncontrol.model.MergeRequest mr = mergeRequestRepository.findById(mergeRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Merge Request not found with ID: " + mergeRequestId));

        if (mr.getStatus() == MergeRequestStatus.MERGED) {
            throw new InvalidSchemaException("Merge Request has already been merged");
        }

        UUID currentSourceHead = mr.getSourceBranch().getHeadVersionId();
        UUID currentTargetHead = mr.getTargetBranch().getHeadVersionId();

        List<MergeRequestApproval> approvals = mergeRequestApprovalRepository.findByMergeRequestId(mr.getId());
        boolean hasValidApproval = approvals.stream().anyMatch(a ->
                !a.getUser().getId().equals(mr.getCreatedBy().getId()) &&
                a.getSourceHeadVersionId().equals(currentSourceHead) &&
                a.getTargetHeadVersionId().equals(currentTargetHead)
        );

        if (!hasValidApproval) {
            log.warn("Merge blocked for MR id={}: no valid peer approval matching current heads", mr.getId());
            throw new InvalidSchemaException("Merge Request must have at least one valid approval matching the current branch heads from a peer reviewer.");
        }

        MergeBranchRequest mergeReq = new MergeBranchRequest(
                mr.getSourceBranch().getName(),
                mr.getTargetBranch().getName(),
                null,
                conflictResolutions
        );

        MergeResultDto result = schemaVersionService.merge(mr.getRepositoryId(), mergeReq, actor);

        if (result.isSuccess()) {
            mr.setStatus(MergeRequestStatus.MERGED);
            mr.setMergedBy(actor);
            mr.setMergedAt(Instant.now());
            mergeRequestRepository.save(mr);

            auditService.recordEvent(
                    mr.getRepositoryId(),
                    actor.getId(),
                    AuditConstants.ACTION_MERGE_REQUEST_MERGED,
                    AuditConstants.ENTITY_MERGE_REQUEST,
                    mr.getId().toString(),
                    "{\"mergedVersionId\":\"" + (result.getMergedVersion() != null ? result.getMergedVersion().getId() : "") + "\"}"
            );
        }

        return result;
    }

    private MergeRequestDto toDto(com.schema.versioncontrol.model.MergeRequest mr, User actor) {
        UUID currentSourceHead = mr.getSourceBranch().getHeadVersionId();
        UUID currentTargetHead = mr.getTargetBranch().getHeadVersionId();

        List<MergeRequestApproval> approvals = mergeRequestApprovalRepository.findByMergeRequestId(mr.getId());
        List<MergeRequestApprovalDto> approvalDtos = approvals.stream().map(a ->
                new MergeRequestApprovalDto(a.getId(), userMapper.toDto(a.getUser()), a.getSourceHeadVersionId(), a.getTargetHeadVersionId(), a.getCreatedAt())
        ).toList();

        boolean hasValidApproval = approvals.stream().anyMatch(a ->
                !a.getUser().getId().equals(mr.getCreatedBy().getId()) &&
                a.getSourceHeadVersionId().equals(currentSourceHead) &&
                a.getTargetHeadVersionId().equals(currentTargetHead)
        );

        MergeRequestStatus computedStatus = mr.getStatus();
        if (mr.getStatus() != MergeRequestStatus.MERGED && mr.getStatus() != MergeRequestStatus.CLOSED) {
            if (hasValidApproval) {
                computedStatus = MergeRequestStatus.APPROVED;
            } else if (mr.getSourceHeadVersionId() != null && (!mr.getSourceHeadVersionId().equals(currentSourceHead) || !mr.getTargetHeadVersionId().equals(currentTargetHead))) {
                computedStatus = MergeRequestStatus.STALE;
            } else {
                computedStatus = MergeRequestStatus.OPEN;
            }
        }

        boolean canApprove = actor != null &&
                             !actor.getId().equals(mr.getCreatedBy().getId()) &&
                             mr.getStatus() != MergeRequestStatus.MERGED;

        boolean canMerge = mr.getStatus() != MergeRequestStatus.MERGED && hasValidApproval;

        DiffResultDto diff = null;
        try {
            diff = schemaVersionService.computeDiff(mr.getRepositoryId(), mr.getSourceBranch().getName(), mr.getTargetBranch().getName());
        } catch (Exception ignored) {}

        return new MergeRequestDto(
                mr.getId(),
                mr.getRepositoryId(),
                branchMapper.toDto(mr.getSourceBranch()),
                branchMapper.toDto(mr.getTargetBranch()),
                currentSourceHead,
                currentTargetHead,
                computedStatus,
                userMapper.toDto(mr.getCreatedBy()),
                mr.getRequestedApprover() != null ? userMapper.toDto(mr.getRequestedApprover()) : null,
                mr.getCreatedAt(),
                mr.getMergedBy() != null ? userMapper.toDto(mr.getMergedBy()) : null,
                mr.getMergedAt(),
                approvalDtos,
                canApprove,
                canMerge,
                diff
        );
    }
}
