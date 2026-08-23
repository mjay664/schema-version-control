package com.schema.versioncontrol.dto;

import com.schema.versioncontrol.constants.DatabaseEngine;
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
public class RepositoryDto {
    private UUID id;
    private String name;
    private DatabaseEngine dbEngine;
    private UserDto createdBy;
    private Instant createdAt;
}
