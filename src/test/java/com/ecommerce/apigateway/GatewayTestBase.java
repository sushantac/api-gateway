package com.ecommerce.apigateway;

import com.ecommerce.apigateway.support.MockUpstream;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.reactive.server.WebTestClient;

public abstract class GatewayTestBase {

    private static final String[][] ROUTE_DEFS = {
            {"auth-service", "Path=/api/v1/auth/**"},
            {"cart-service", "Path=/api/v1/cart/**"},
            {"product-service", "Path=/api/v1/products/**,/api/v1/categories/**"},
            {"order-api", "Path=/api/v1/orders/**,/api/v1/customers/**"},
            {"admin-service", "Path=/api/v1/admin/**"}
    };

    @LocalServerPort
    protected int port;

    protected WebTestClient client;

    @BeforeEach
    void setUpClient() {
        client = WebTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
    }

    protected static void bindRoutes(DynamicPropertyRegistry registry, MockUpstream upstream) {
        for (int i = 0; i < ROUTE_DEFS.length; i++) {
            final int index = i;
            registry.add("spring.cloud.gateway.routes[" + index + "].id", () -> ROUTE_DEFS[index][0]);
            registry.add("spring.cloud.gateway.routes[" + index + "].uri", upstream::baseUrl);
            registry.add("spring.cloud.gateway.routes[" + index + "].predicates[0]", () -> ROUTE_DEFS[index][1]);
        }
    }
}