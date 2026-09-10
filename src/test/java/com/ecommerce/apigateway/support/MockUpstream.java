package com.ecommerce.apigateway.support;

import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class MockUpstream implements AutoCloseable {

    private final WebServer server;

    private MockUpstream(WebServer server) {
        this.server = server;
    }

    public static MockUpstream start() {
        NettyReactiveWebServerFactory factory = new NettyReactiveWebServerFactory();
        factory.setPort(0);
        WebServer server = factory.getWebServer(RouterFunctions.toHttpHandler(routes()));
        server.start();
        return new MockUpstream(server);
    }

    public String baseUrl() {
        return "http://localhost:" + server.getPort();
    }

    @Override
    public void close() {
        server.stop();
    }

    private static RouterFunction<ServerResponse> routes() {
        return RouterFunctions.route(request -> true, MockUpstream::handle);
    }

    private static Mono<ServerResponse> handle(ServerRequest request) {
        String path = request.path();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("path", path);
        body.put("echoUserId", Optional.ofNullable(request.headers().firstHeader("X-User-Id")).orElse("ABSENT"));
        body.put("echoRole", Optional.ofNullable(request.headers().firstHeader("X-User-Role")).orElse("ABSENT"));

        if (path.endsWith("/login") || path.endsWith("/register")) {
            body.put("accessToken", "test-access-token-value");
            body.put("refreshToken", "test-refresh-token-value");
            return request.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .flatMap(raw -> {
                        body.put("receivedBody", raw);
                        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(body);
                    });
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(body);
    }
}