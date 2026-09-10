package com.ecommerce.apigateway.filter;

import com.ecommerce.apigateway.config.RateLimitProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String TOO_MANY_REQUESTS_BODY = "{\"message\":\"Too many requests\"}";

    private final RateLimitProperties properties;
    private final SlidingWindowLimiter limiter;

    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
        this.limiter = new SlidingWindowLimiter(properties.minutes() * 60_000L);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.enabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();
        String clientId = resolveClientId(exchange);
        int limit = properties.isAuthPath(path) ? properties.authLimit() : properties.defaultLimit();

        if (limiter.tryAcquire(clientId, limit)) {
            return chain.filter(exchange);
        }

        long retryAfter = limiter.retryAfterSeconds(clientId);
        log.warn("Rate limit exceeded for {} on {} (limit {})", clientId, path, limit);
        return writeTooManyRequests(exchange.getResponse(), retryAfter);
    }

    private String resolveClientId(ServerWebExchange exchange) {
        String forwardedFor = exchange.getRequest().getHeaders().getFirst(X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote != null && remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : "unknown";
    }

    private Mono<Void> writeTooManyRequests(ServerHttpResponse response, long retryAfter) {
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = TOO_MANY_REQUESTS_BODY.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    static final class SlidingWindowLimiter {

        private final ConcurrentMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
        private final long windowMillis;

        SlidingWindowLimiter(long windowMillis) {
            this.windowMillis = windowMillis;
        }

        boolean tryAcquire(String key, int limit) {
            long now = System.currentTimeMillis();
            long cutoff = now - windowMillis;
            Deque<Long> deque = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() <= cutoff) {
                    deque.pollFirst();
                }
                if (deque.size() >= limit) {
                    return false;
                }
                deque.addLast(now);
                return true;
            }
        }

        long retryAfterSeconds(String key) {
            Deque<Long> deque = windows.get(key);
            if (deque == null) {
                return Math.max(windowMillis / 1000L, 1);
            }
            synchronized (deque) {
                if (deque.isEmpty()) {
                    return Math.max(windowMillis / 1000L, 1);
                }
                long retryMillis = deque.peekFirst() + windowMillis - System.currentTimeMillis();
                return Math.max((retryMillis + 999) / 1000L, 1);
            }
        }
    }
}