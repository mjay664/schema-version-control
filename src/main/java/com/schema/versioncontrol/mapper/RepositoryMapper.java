package com.schema.versioncontrol.mapper;

import com.schema.versioncontrol.dto.RepositoryDto;
import com.schema.versioncontrol.model.RepositoryEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RepositoryMapper {

    private final UserMapper userMapper;

    public RepositoryDto toDto(RepositoryEntity repo) {
        if (repo == null) return null;
        return new RepositoryDto(
                repo.getId(),
                repo.getName(),
                repo.getDbEngine(),
                userMapper.toDto(repo.getCreatedBy()),
                repo.getCreatedAt()
        );
    }
}
