package com.schema.versioncontrol.service.impl;

import com.schema.versioncontrol.dto.AuthResponse;
import com.schema.versioncontrol.dto.LoginRequest;
import com.schema.versioncontrol.dto.RegisterRequest;
import com.schema.versioncontrol.dto.UserDto;
import com.schema.versioncontrol.exception.DuplicateResourceException;

import com.schema.versioncontrol.mapper.UserMapper;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.repository.UserRepository;
import com.schema.versioncontrol.service.AuditService;
import com.schema.versioncontrol.service.JwtService;
import com.schema.versioncontrol.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AuditService auditService;

    @Override
    @Transactional
    public AuthResponse registerUser(RegisterRequest request) {
        log.info("Registering new user with email='{}'", request.getEmail());
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email '{}' already exists", request.getEmail());
            throw new DuplicateResourceException("User with email '" + request.getEmail() + "' already exists");
        }
        String passwordHash = passwordEncoder.encode(request.getPassword());
        User user = new User(request.getEmail(), passwordHash, request.getDisplayName());
        user = userRepository.save(user);

        String jwt = jwtService.generateToken(user);

        // Record audit event
        auditService.recordEvent(null, user.getId(), "USER_REGISTERED", "USER", user.getId().toString(),
                "{\"displayName\":\"" + user.getDisplayName() + "\",\"email\":\"" + user.getEmail() + "\"}");

        log.info("User registered successfully: id={}, email='{}'", user.getId(), user.getEmail());
        return new AuthResponse(jwt, userMapper.toDto(user));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse authenticate(LoginRequest request) {
        log.info("Authentication attempt for email='{}'", request.getEmail());
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    log.warn("Authentication failed: email '{}' not found", request.getEmail());
                    return new IllegalArgumentException("Invalid email or password");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Authentication failed: incorrect password for email='{}'", request.getEmail());
            throw new IllegalArgumentException("Invalid email or password");
        }

        String jwt = jwtService.generateToken(user);
        log.info("User authenticated successfully: email='{}'", user.getEmail());
        return new AuthResponse(jwt, userMapper.toDto(user));
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserDto> findById(UUID id) {
        return userRepository.findById(id).map(userMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<UserDto> getAllUsers() {
        return userRepository.findAll().stream().map(userMapper::toDto).toList();
    }
}
