package com.schema.versioncontrol.service;

import com.schema.versioncontrol.dto.CreateMergeRequestRequest;
import com.schema.versioncontrol.dto.MergeRequestDto;
import com.schema.versioncontrol.dto.MergeResultDto;
import com.schema.versioncontrol.model.User;
import java.util.List;
import java.util.UUID;

public interface MergeRequestService {
    MergeRequestDto createMergeRequest(CreateMergeRequestRequest request, User actor);
    List<MergeRequestDto> getMergeRequestsForRepo(UUID repositoryId, User actor);
    MergeRequestDto getMergeRequestDetails(UUID mergeRequestId, User actor);
    MergeRequestDto approveMergeRequest(UUID mergeRequestId, User actor);
    MergeResultDto mergeMergeRequest(UUID mergeRequestId, java.util.Map<String, String> conflictResolutions, User actor);

    /** Merge with no conflict decisions; blocks if the three-way merge conflicts. */
    default MergeResultDto mergeMergeRequest(UUID mergeRequestId, User actor) {
        return mergeMergeRequest(mergeRequestId, null, actor);
    }
}
