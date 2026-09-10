package com.ecommerce.apigateway;

import com.ecommerce.apigateway.support.MockUpstream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.name=application-test",
        "app.rate-limit.enabled=true",
        "app.rate-limit.default-limit=3",
        "app.rate-limit.auth-limit=2",
        "app.rate-limit.minutes=1"
})
class RateLimitFilterTest extends GatewayTestBase {

    private static final MockUpstream UPSTREAM = MockUpstream.start();

    @DynamicPropertySource
    static void bindMock(DynamicPropertyRegistry registry) {
        bindRoutes(registry, UPSTREAM);
    }

    @AfterAll
    static void stopUpstream() {
        UPSTREAM.close();
    }

    @Test
    void returns429WithRetryAfterOnceAuthLimitExceeded() {
        String clientIp = "192.0.2.10";
        for (int i = 0; i < 2; i++) {
            client.post().uri("/api/v1/auth/login")
                    .header("X-Forwarded-For", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"a@b.c\",\"password\":\"pw\"}")
                    .exchange()
                    .expectStatus().isOk();
        }
        client.post().uri("/api/v1/auth/login")
                .header("X-Forwarded-For", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"email\":\"a@b.c\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectHeader().valueMatches(HttpHeaders.RETRY_AFTER, "\\d+")
                .expectBody().jsonPath("$.message").isEqualTo("Too many requests");
    }

    @Test
    void appliesDefaultLimitToOtherRoutes() {
        String clientIp = "192.0.2.11";
        for (int i = 0; i < 3; i++) {
            client.get().uri("/api/v1/products")
                    .header("X-Forwarded-For", clientIp)
                    .exchange()
                    .expectStatus().isOk();
        }
        client.get().uri("/api/v1/products")
                .header("X-Forwarded-For", clientIp)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}