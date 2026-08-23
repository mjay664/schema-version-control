package com.schema.versioncontrol.service;

import com.schema.versioncontrol.dto.*;
import com.schema.versioncontrol.model.User;

import java.util.List;
import java.util.UUID;

public interface SchemaVersionService {
    RepositoryDto createRepository(CreateRepoRequest request, User actor);
    List<RepositoryDto> getAllRepositories(int page, int size);
    RepositoryDto getRepository(UUID repoId);
    
    List<BranchDto> getBranches(UUID repoId, int page, int size);
    BranchDto createBranch(UUID repoId, CreateBranchRequest request, User actor);
    
    SchemaVersionDto commitSchema(UUID repoId, CommitSchemaRequest request, User actor);
    List<SchemaVersionDto> getVersionHistory(UUID repoId);
    
    DiffResultDto computeDiff(UUID repoId, String sourceBranchName, String targetBranchName);
    MergeResultDto merge(UUID repoId, MergeBranchRequest request, User actor);
}
