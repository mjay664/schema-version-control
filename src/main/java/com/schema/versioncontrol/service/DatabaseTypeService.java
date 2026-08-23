package com.schema.versioncontrol.service;

import com.schema.versioncontrol.constants.DatabaseEngine;
import com.schema.versioncontrol.dto.ConstraintTypeDto;
import com.schema.versioncontrol.dto.DataTypeDto;

import java.util.List;
import java.util.UUID;

public interface DatabaseTypeService {
    List<DataTypeDto> getDataTypesForEngine(DatabaseEngine engine);
    List<DataTypeDto> getDataTypesForRepository(UUID repositoryId);
    List<ConstraintTypeDto> getConstraintsForEngine(DatabaseEngine engine);
    List<ConstraintTypeDto> getConstraintsForRepository(UUID repositoryId);
    void validateSchemaDataTypes(String schemaJson, DatabaseEngine engine);
}
