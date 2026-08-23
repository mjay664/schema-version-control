package com.schema.versioncontrol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintTypeDto {
    private String name;
    private String category;
    private String description;
    private boolean parameterized;
}
