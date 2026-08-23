package com.schema.versioncontrol.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataTypeDto {
    private String name;
    private String category; // e.g. Numeric, Text, Date/Time, JSON, Binary
    private String description;
    private boolean parameterized; // e.g. VARCHAR(n)
}
