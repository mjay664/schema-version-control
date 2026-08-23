package com.schema.versioncontrol.constants;

public final class SecurityConstants {

    private SecurityConstants() {}

    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final long DEFAULT_EXPIRATION_MS = 86400000L; // 24 Hours
}
