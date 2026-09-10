package com.ecommerce.apigateway;

import com.ecommerce.apigateway.support.JwtFactory;
import com.ecommerce.apigateway.support.MockUpstream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.name=application-test",
        "app.rate-limit.enabled=false"
})
class AuthHeaderEnrichmentFilterTest extends GatewayTestBase {

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
    void validJwtAddsUserIdAndRoleHeadersDownstream() {
        String token = JwtFactory.createToken("42", "ADMIN");
        client.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echoUserId").isEqualTo("42")
                .jsonPath("$.echoRole").isEqualTo("ADMIN");
    }

    @Test
    void missingTokenPassesThroughWithoutHeaders() {
        client.get().uri("/api/v1/products")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echoUserId").isEqualTo("ABSENT")
                .jsonPath("$.echoRole").isEqualTo("ABSENT");
    }

    @Test
    void invalidTokenPassesThroughWithoutHeaders() {
        client.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echoUserId").isEqualTo("ABSENT")
                .jsonPath("$.echoRole").isEqualTo("ABSENT");
    }
}