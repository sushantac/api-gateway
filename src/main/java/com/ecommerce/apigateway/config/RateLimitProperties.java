package com.ecommerce.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int defaultLimit,
        int minutes,
        int authLimit,
        List<String> authPaths) {

    public RateLimitProperties {
        if (defaultLimit <= 0) {
            defaultLimit = 100;
        }
        if (minutes <= 0) {
            minutes = 1;
        }
        if (authLimit <= 0) {
            authLimit = 10;
        }
        if (authPaths == null || authPaths.isEmpty()) {
            authPaths = List.of("/api/v1/auth/login", "/api/v1/auth/register");
        }
    }

    public boolean isAuthPath(String path) {
        return authPaths.stream().anyMatch(path::startsWith);
    }
}