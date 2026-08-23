package com.schema.versioncontrol.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Refuses to serve a deployment that is still carrying development defaults.
 *
 * The JWT secret and the wide-open CORS policy are convenient locally and
 * dangerous in production: the committed secret would let anyone mint a valid
 * token for any account. Failing at startup makes a misconfigured deploy
 * obvious immediately rather than silently insecure.
 */
@Slf4j
@Component
@Profile("prod")
public class DeploymentSanityCheck {

    private static final String DEV_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @EventListener(ApplicationReadyEvent.class)
    public void verify() {
        if (DEV_SECRET.equals(jwtSecret)) {
            throw new IllegalStateException(
                    "JWT_SECRET is still the development default. Set it to a unique value "
                            + "(for example: openssl rand -hex 32) before deploying.");
        }
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters to key HS512 safely.");
        }
        if ("*".equals(allowedOrigins.trim())) {
            log.warn("CORS_ALLOWED_ORIGINS is '*'. Set it to the frontend origin so only "
                    + "your own site can call this API with credentials.");
        }
        log.info("Deployment configuration checks passed.");
    }
}
