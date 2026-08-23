package com.schema.versioncontrol.service;

import com.schema.versioncontrol.model.User;
import java.util.UUID;

public interface JwtService {
    String generateToken(User user);
    String extractEmail(String token);
    UUID extractUserId(String token);
    boolean isTokenValid(String token, String userEmail);
}
