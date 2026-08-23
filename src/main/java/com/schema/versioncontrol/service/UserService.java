package com.schema.versioncontrol.service;

import com.schema.versioncontrol.dto.AuthResponse;
import com.schema.versioncontrol.dto.LoginRequest;
import com.schema.versioncontrol.dto.RegisterRequest;
import com.schema.versioncontrol.dto.UserDto;
import com.schema.versioncontrol.model.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserService {
    AuthResponse registerUser(RegisterRequest request);
    AuthResponse authenticate(LoginRequest request);
    User findUserEntityByEmail(String email);
    Optional<UserDto> findById(UUID id);
    List<UserDto> getAllUsers();
}
