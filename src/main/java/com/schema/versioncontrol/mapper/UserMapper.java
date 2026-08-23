package com.schema.versioncontrol.mapper;

import com.schema.versioncontrol.dto.UserDto;
import com.schema.versioncontrol.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserDto toDto(User user) {
        if (user == null) return null;
        return new UserDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
