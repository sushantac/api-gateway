package com.ecommerce.apigateway;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecommerce.apigateway.filter.RequestLoggingFilter;
import com.ecommerce.apigateway.support.MockUpstream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.name=application-test",
        "app.rate-limit.enabled=false"
})
class RequestLoggingFilterTest extends GatewayTestBase {

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
    void logsStructuredAccessEntryWithPiiMasked() {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            client.post().uri("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("{\"email\":\"a@b.c\",\"password\":\"super-secret-123\",\"token\":\"jwt-token-abc\"}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.echoUserId").isEqualTo("ABSENT");

            List<String> messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();

            assertThat(messages).anySatisfy(message -> {
                assertThat(message)
                        .contains("\"service\":\"auth-service\"")
                        .contains("\"method\":\"POST\"")
                        .contains("\"path\":\"/api/v1/auth/login\"")
                        .contains("\"status\":200")
                        .contains("***")
                        .doesNotContain("super-secret-123")
                        .doesNotContain("jwt-token-abc")
                        .doesNotContain("test-access-token-value");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void forwardsUnmaskedBodyToDownstream() {
        String body = "{\"email\":\"a@b.c\",\"password\":\"super-secret-123\"}";
        client.post().uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.receivedBody").isEqualTo(body);
    }
}