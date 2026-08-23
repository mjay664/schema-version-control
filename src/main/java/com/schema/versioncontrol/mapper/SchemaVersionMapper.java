package com.schema.versioncontrol.mapper;

import com.schema.versioncontrol.dto.SchemaVersionDto;
import com.schema.versioncontrol.model.SchemaVersion;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchemaVersionMapper {

    private final UserMapper userMapper;

    public SchemaVersionDto toDto(SchemaVersion version) {
        if (version == null) return null;
        return new SchemaVersionDto(
                version.getId(),
                version.getRepositoryId(),
                version.getSchemaData(),
                version.getParentVersionIds(),
                version.getCommitMessage(),
                userMapper.toDto(version.getCreatedBy()),
                version.getCreatedAt()
        );
    }
}
