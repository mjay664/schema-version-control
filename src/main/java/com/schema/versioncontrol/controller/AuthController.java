package com.schema.versioncontrol.controller;

import com.schema.versioncontrol.dto.AuthResponse;
import com.schema.versioncontrol.dto.LoginRequest;
import com.schema.versioncontrol.dto.RegisterRequest;
import com.schema.versioncontrol.dto.UserDto;
import com.schema.versioncontrol.mapper.UserMapper;
import com.schema.versioncontrol.model.User;
import com.schema.versioncontrol.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final UserMapper userMapper;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("POST /api/auth/register — email='{}'", request.getEmail());
        AuthResponse response = userService.registerUser(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login — email='{}'", request.getEmail());
        AuthResponse response = userService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userMapper.toDto(currentUser));
    }

    @GetMapping("/users")
    public ResponseEntity<java.util.List<UserDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
}
