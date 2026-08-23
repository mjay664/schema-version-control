package com.schema.versioncontrol.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schema.versioncontrol.constants.DatabaseEngine;
import com.schema.versioncontrol.dto.ConstraintTypeDto;
import com.schema.versioncontrol.dto.DataTypeDto;
import com.schema.versioncontrol.exception.InvalidSchemaException;
import com.schema.versioncontrol.exception.ResourceNotFoundException;
import com.schema.versioncontrol.model.RepositoryEntity;
import com.schema.versioncontrol.repository.RepositoryRepository;
import com.schema.versioncontrol.service.DatabaseTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseTypeServiceImpl implements DatabaseTypeService {

    private final RepositoryRepository repositoryRepository;
    private final ObjectMapper objectMapper;

    // Pattern for parameterized safe SQL types e.g. VARCHAR(255), DECIMAL(10, 2), INT
    private static final Pattern SAFE_TYPE_PATTERN = Pattern.compile("^[A-Za-z0-9_]+(\\(\\s*\\d+(\\s*,\\s*\\d+)?\\s*\\))?$", Pattern.CASE_INSENSITIVE);

    @Override
    public List<DataTypeDto> getDataTypesForEngine(DatabaseEngine engine) {
        DatabaseEngine target = engine != null ? engine : DatabaseEngine.GENERIC;
        return getEngineTypes(target);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DataTypeDto> getDataTypesForRepository(UUID repositoryId) {
        RepositoryEntity repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repositoryId));
        return getDataTypesForEngine(repo.getDbEngine());
    }

    @Override
    public List<ConstraintTypeDto> getConstraintsForEngine(DatabaseEngine engine) {
        DatabaseEngine target = engine != null ? engine : DatabaseEngine.GENERIC;
        return getEngineConstraints(target);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConstraintTypeDto> getConstraintsForRepository(UUID repositoryId) {
        RepositoryEntity repo = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Repository not found with ID: " + repositoryId));
        return getConstraintsForEngine(repo.getDbEngine());
    }

    @Override
    public void validateSchemaDataTypes(String schemaJson, DatabaseEngine engine) {
        if (schemaJson == null || schemaJson.isBlank()) return;

        try {
            Map<String, Object> root = objectMapper.readValue(schemaJson, new TypeReference<Map<String, Object>>() {});
            Object tablesObj = root.get("tables");
            if (!(tablesObj instanceof List)) return;

            List<?> tables = (List<?>) tablesObj;
            for (Object tableObj : tables) {
                if (tableObj instanceof Map) {
                    Map<?, ?> table = (Map<?, ?>) tableObj;
                    String tableName = String.valueOf(table.get("name"));
                    Object colsObj = table.get("columns");
                    if (colsObj instanceof List) {
                        List<?> columns = (List<?>) colsObj;
                        for (Object colObj : columns) {
                            if (colObj instanceof Map) {
                                Map<?, ?> col = (Map<?, ?>) colObj;
                                String colName = String.valueOf(col.get("name"));
                                String rawType = String.valueOf(col.get("type"));

                                validateSingleType(tableName, colName, rawType, engine);
                            }
                        }
                    }
                }
            }
        } catch (InvalidSchemaException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidSchemaException("Failed to parse schema JSON for data type validation: " + e.getMessage());
        }
    }

    private void validateSingleType(String tableName, String colName, String type, DatabaseEngine engine) {
        if (type == null || type.isBlank() || type.equalsIgnoreCase("null")) {
            throw new InvalidSchemaException("Column '" + colName + "' in table '" + tableName + "' has an empty data type");
        }

        String trimmed = type.trim();
        if (!SAFE_TYPE_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidSchemaException("Column '" + colName + "' in table '" + tableName + "' contains invalid data type syntax: '" + type + "'");
        }
    }

    private List<DataTypeDto> getEngineTypes(DatabaseEngine engine) {
        switch (engine) {
            case POSTGRESQL:
                return List.of(
                        new DataTypeDto("UUID", "Identifier", "Universally Unique Identifier", false),
                        new DataTypeDto("VARCHAR(255)", "Text", "Variable-length character string", true),
                        new DataTypeDto("TEXT", "Text", "Unlimited length text string", false),
                        new DataTypeDto("INTEGER", "Numeric", "4-byte signed integer", false),
                        new DataTypeDto("BIGINT", "Numeric", "8-byte signed integer", false),
                        new DataTypeDto("NUMERIC(10,2)", "Numeric", "Exact numeric with configurable precision", true),
                        new DataTypeDto("BOOLEAN", "Logical", "True or false boolean", false),
                        new DataTypeDto("TIMESTAMP", "Date/Time", "Date and time without time zone", false),
                        new DataTypeDto("TIMESTAMPTZ", "Date/Time", "Date and time with time zone", false),
                        new DataTypeDto("JSONB", "Document", "Binary JSON data", false),
                        new DataTypeDto("BYTEA", "Binary", "Binary data bytes", false)
                );
            case MYSQL:
                return List.of(
                        new DataTypeDto("INT", "Numeric", "4-byte integer", false),
                        new DataTypeDto("BIGINT", "Numeric", "8-byte integer", false),
                        new DataTypeDto("VARCHAR(255)", "Text", "Variable length string", true),
                        new DataTypeDto("LONGTEXT", "Text", "Long text data", false),
                        new DataTypeDto("DATETIME", "Date/Time", "Date and time format", false),
                        new DataTypeDto("TINYINT(1)", "Logical", "Boolean representation", false),
                        new DataTypeDto("JSON", "Document", "Native JSON format", false),
                        new DataTypeDto("DECIMAL(10,2)", "Numeric", "Fixed precision number", true),
                        new DataTypeDto("BLOB", "Binary", "Binary large object", false)
                );
            case SQLITE:
                return List.of(
                        new DataTypeDto("INTEGER", "Numeric", "Signed integer", false),
                        new DataTypeDto("TEXT", "Text", "Text string", false),
                        new DataTypeDto("REAL", "Numeric", "Floating point value", false),
                        new DataTypeDto("NUMERIC", "Numeric", "Numeric affinity type", false),
                        new DataTypeDto("BLOB", "Binary", "Binary blob", false)
                );
            case ORACLE:
                return List.of(
                        new DataTypeDto("NUMBER", "Numeric", "Variable length number", true),
                        new DataTypeDto("VARCHAR2(255)", "Text", "Variable-length character string", true),
                        new DataTypeDto("CLOB", "Text", "Character large object", false),
                        new DataTypeDto("DATE", "Date/Time", "Date and time format", false),
                        new DataTypeDto("TIMESTAMP", "Date/Time", "Timestamp with fractional seconds", false),
                        new DataTypeDto("RAW(16)", "Binary", "Raw binary bytes", true)
                );
            case GENERIC:
            default:
                return List.of(
                        new DataTypeDto("VARCHAR(255)", "Text", "Variable character string", true),
                        new DataTypeDto("TEXT", "Text", "Text string", false),
                        new DataTypeDto("INTEGER", "Numeric", "Standard integer", false),
                        new DataTypeDto("BIGINT", "Numeric", "Big integer", false),
                        new DataTypeDto("DECIMAL(10,2)", "Numeric", "Fixed point decimal", true),
                        new DataTypeDto("BOOLEAN", "Logical", "Boolean flag", false),
                        new DataTypeDto("TIMESTAMP", "Date/Time", "Date timestamp", false),
                        new DataTypeDto("UUID", "Identifier", "UUID string", false),
                        new DataTypeDto("BLOB", "Binary", "Binary data", false)
                );
        }
    }

    private List<ConstraintTypeDto> getEngineConstraints(DatabaseEngine engine) {
        List<ConstraintTypeDto> common = List.of(
                new ConstraintTypeDto("PRIMARY KEY", "Key", "Designates the column as primary key", false),
                new ConstraintTypeDto("NOT NULL", "Nullability", "Enforces non-null column values", false),
                new ConstraintTypeDto("UNIQUE", "Key", "Enforces unique values across table rows", false),
                new ConstraintTypeDto("DEFAULT", "Expression", "Assigns default value expression", true),
                new ConstraintTypeDto("FOREIGN KEY", "Relation", "References foreign table and column", true),
                new ConstraintTypeDto("CHECK", "Validation", "Enforces check condition expression", true)
        );

        List<ConstraintTypeDto> engineSpecific = switch (engine) {
            case POSTGRESQL -> List.of(
                    new ConstraintTypeDto("SERIAL", "Generator", "4-byte auto-incrementing integer sequence", false),
                    new ConstraintTypeDto("BIGSERIAL", "Generator", "8-byte auto-incrementing integer sequence", false),
                    new ConstraintTypeDto("GENERATED ALWAYS AS IDENTITY", "Generator", "ANSI SQL standard identity generator", false)
            );
            case MYSQL -> List.of(
                    new ConstraintTypeDto("AUTO_INCREMENT", "Generator", "Auto-incrementing integer identifier", false)
            );
            case SQLITE -> List.of(
                    new ConstraintTypeDto("AUTOINCREMENT", "Generator", "SQLite primary key auto-increment", false)
            );
            case ORACLE -> List.of(
                    new ConstraintTypeDto("GENERATED ALWAYS AS IDENTITY", "Generator", "Oracle identity column sequence", false)
            );
            default -> List.of(
                    new ConstraintTypeDto("AUTO_INCREMENT", "Generator", "Auto-incrementing integer identifier", false)
            );
        };

        List<ConstraintTypeDto> result = new ArrayList<>(common);
        result.addAll(engineSpecific);
        return result;
    }
}
