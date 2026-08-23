package com.schema.versioncontrol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Optional body for merging a merge request.
 *
 * Conflicts are settled by choosing a side per conflicting path rather than by
 * posting a replacement schema, so a merge can only ever land a definition that
 * already exists on one of the two branches under review.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveConflictsRequest {

    /** Conflict key ("table" or "table.column") to "TARGET" or "SOURCE". */
    private Map<String, String> resolutions;
}
