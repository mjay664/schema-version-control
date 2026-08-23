package com.schema.versioncontrol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiffResultDto {
    private String sourceBranch;
    private String targetBranch;
    private UserDto sourceHeadUser;
    private UserDto targetHeadUser;
    private String ancestorVersionId;
    private List<String> addedTables;
    private List<String> removedTables;
    private List<String> modifiedTables;
    private List<Map<String, Object>> detailedChanges;
}
