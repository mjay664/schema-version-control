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
public class MergeResultDto {
    private boolean success;
    private boolean hasConflicts;
    private String mergedSchemaData;
    private SchemaVersionDto mergedVersion;
    private List<Map<String, Object>> conflicts;
}
