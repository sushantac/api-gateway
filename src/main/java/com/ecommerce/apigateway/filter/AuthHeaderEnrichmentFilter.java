package com.ecommerce.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Collection;

@Component
public class AuthHeaderEnrichmentFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AuthHeaderEnrichmentFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_USER_ID = "X-User-Id";
    private static final String X_USER_ROLE = "X-User-Role";

    private final JwtDecoder jwtDecoder;

    public AuthHeaderEnrichmentFilter(JwtDecoder jwtDecoder) {
        this.jwtDecoder = jwtDecoder;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }

        Jwt jwt = decodeQuietly(authorization, exchange);
        if (jwt == null) {
            return chain.filter(exchange);
        }

        String userId = jwt.getSubject();
        String role = roleFrom(jwt);
        if (userId == null && role == null) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> {
                    if (userId != null) {
                        headers.add(X_USER_ID, userId);
                    }
                    if (role != null) {
                        headers.add(X_USER_ROLE, role);
                    }
                })
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private Jwt decodeQuietly(String authorization, ServerWebExchange exchange) {
        try {
            return jwtDecoder.decode(authorization.substring(BEARER_PREFIX.length()));
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT on {}: {}", exchange.getRequest().getURI().getPath(), e.getMessage());
            return null;
        }
    }

    private String roleFrom(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        if (role != null) {
            return role;
        }
        Object roles = jwt.getClaims().get("roles");
        if (roles instanceof Collection<?> collection && !collection.isEmpty()) {
            return String.valueOf(collection.iterator().next());
        }
        return null;
    }
}