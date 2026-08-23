package com.schema.versioncontrol.mapper;

import com.schema.versioncontrol.dto.BranchDto;
import com.schema.versioncontrol.model.Branch;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BranchMapper {

    private final UserMapper userMapper;

    public BranchDto toDto(Branch branch) {
        if (branch == null) return null;
        return new BranchDto(
                branch.getId(),
                branch.getRepositoryId(),
                branch.getName(),
                branch.getSourceBranchName(),
                branch.getHeadVersionId(),
                userMapper.toDto(branch.getCreatedBy()),
                branch.getCreatedAt()
        );
    }
}
