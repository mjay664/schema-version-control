package com.schema.versioncontrol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MergeRequestApprovalDto {
    private UUID id;
    private UserDto user;
    private UUID sourceHeadVersionId;
    private UUID targetHeadVersionId;
    private Instant createdAt;
}
