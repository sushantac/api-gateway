package com.ecommerce.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final int MAX_CAPTURED_BODY_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    public RequestLoggingFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Instant start = Instant.now();
        ServerHttpRequest request = exchange.getRequest();

        CachedBodyServerHttpRequestDecorator requestDecorator;
        if (shouldCaptureBody(request)) {
            requestDecorator = new CachedBodyServerHttpRequestDecorator(request);
            exchange = exchange.mutate().request(requestDecorator).build();
        } else {
            requestDecorator = null;
        }

        CachedBodyServerHttpResponseDecorator responseDecorator =
                new CachedBodyServerHttpResponseDecorator(exchange.getResponse());
        ServerWebExchange loggingExchange = exchange.mutate().response(responseDecorator).build();

        return chain.filter(loggingExchange)
                .doFinally(signal -> logRequest(loggingExchange, request, start, requestDecorator, responseDecorator));
    }

    private boolean shouldCaptureBody(ServerHttpRequest request) {
        String contentType = request.getHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("json")) {
            return false;
        }
        long contentLength = -1;
        try {
            contentLength = request.getHeaders().getContentLength();
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return contentLength < 0 || contentLength <= MAX_CAPTURED_BODY_BYTES;
    }

    private void logRequest(ServerWebExchange exchange,
                            ServerHttpRequest request,
                            Instant start,
                            CachedBodyServerHttpRequestDecorator requestDecorator,
                            CachedBodyServerHttpResponseDecorator responseDecorator) {
        try {
            Map<String, Object> entry = new LinkedHashMap<>();
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            entry.put("service", route != null && route.getId() != null ? route.getId() : "api-gateway");
            entry.put("method", request.getMethod() != null ? request.getMethod().name() : "-");
            entry.put("path", request.getURI().getPath());
            String query = PiiMasker.maskQuery(request.getURI().getRawQuery());
            if (query != null && !query.isEmpty()) {
                entry.put("query", query);
            }
            HttpStatusCode status = exchange.getResponse().getStatusCode();
            entry.put("status", status != null ? status.value() : 0);
            entry.put("durationMs", Duration.between(start, Instant.now()).toMillis());
            if (requestDecorator != null) {
                String body = maskedBody(requestDecorator.cachedBody());
                if (body != null) {
                    entry.put("requestBody", body);
                }
            }
            String responseBody = maskedBody(responseDecorator.cachedBody());
            if (responseBody != null) {
                entry.put("responseBody", responseBody);
            }
            log.info(objectMapper.writeValueAsString(entry));
        } catch (Exception e) {
            log.warn("Access log failed for {}", request.getURI().getPath(), e);
        }
    }

    private String maskedBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return text.isBlank() ? null : PiiMasker.maskJson(text);
    }

    static final class CachedBodyServerHttpRequestDecorator extends ServerHttpRequestDecorator {

        private volatile byte[] cachedBytes;

        CachedBodyServerHttpRequestDecorator(ServerHttpRequest delegate) {
            super(delegate);
        }

        @Override
        public Flux<DataBuffer> getBody() {
            if (cachedBytes != null) {
                return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(cachedBytes));
            }
            return super.getBody()
                    .collectList()
                    .flatMapMany(buffers -> {
                        cachedBytes = toBytes(buffers);
                        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(cachedBytes));
                    });
        }

        byte[] cachedBody() {
            return cachedBytes;
        }
    }

    static final class CachedBodyServerHttpResponseDecorator extends ServerHttpResponseDecorator {

        private volatile byte[] cachedBytes;

        CachedBodyServerHttpResponseDecorator(ServerHttpResponse delegate) {
            super(delegate);
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return DataBufferUtils.join(body).flatMap(dataBuffer -> {
                byte[] bytes = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(bytes);
                DataBufferUtils.release(dataBuffer);
                cachedBytes = bytes;
                return getDelegate().writeWith(Mono.just(getDelegate().bufferFactory().wrap(bytes)));
            });
        }

        byte[] cachedBody() {
            return cachedBytes;
        }
    }

    private static byte[] toBytes(List<DataBuffer> buffers) {
        int size = buffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
        byte[] all = new byte[size];
        int position = 0;
        for (DataBuffer buffer : buffers) {
            int length = buffer.readableByteCount();
            buffer.read(all, position, length);
            position += length;
            DataBufferUtils.release(buffer);
        }
        return all;
    }
}