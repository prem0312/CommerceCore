package com.commercecore.gateway.filter;

import com.commercecore.gateway.config.JwtProperties;
import com.commercecore.gateway.dto.ErrorResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class JwtAuthenticationGatewayFilterFactory
        extends AbstractGatewayFilterFactory<JwtAuthenticationGatewayFilterFactory.Config> {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationGatewayFilterFactory.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationGatewayFilterFactory(JwtProperties jwtProperties, ObjectMapper objectMapper) {
        super(Config.class);
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
    }

    public static class Config {
        private boolean requireAdmin = false;

        public boolean isRequireAdmin() {
            return requireAdmin;
        }

        public void setRequireAdmin(boolean requireAdmin) {
            this.requireAdmin = requireAdmin;
        }
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();
            HttpMethod method = request.getMethod();

            // Allow public endpoints
            if (isPublicEndpoint(path, method)) {
                // If token is present, we still try to parse claims and forward headers
                String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
                if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                    try {
                        Claims claims = parseClaims(authHeader.substring(BEARER_PREFIX.length()).trim());
                        ServerHttpRequest mutatedRequest = mutateRequestWithClaims(request, claims);
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    } catch (Exception ex) {
                        log.debug("Optional JWT parsing failed on public route: {}", ex.getMessage());
                    }
                }
                return chain.filter(exchange);
            }

            // Protected endpoint requires Authorization header
            String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Missing or malformed Authorization header", "UNAUTHORIZED");
            }

            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            try {
                Claims claims = parseClaims(token);
                String role = claims.get("role", String.class);

                if (config.isRequireAdmin() && !"ADMIN".equalsIgnoreCase(role)) {
                    return onError(exchange, HttpStatus.FORBIDDEN, "Access denied: Admin privileges required", "FORBIDDEN");
                }

                ServerHttpRequest mutatedRequest = mutateRequestWithClaims(request, claims);
                return chain.filter(exchange.mutate().request(mutatedRequest).build());
            } catch (JwtException ex) {
                log.warn("JWT validation failed for path {}: {}", path, ex.getMessage());
                return onError(exchange, HttpStatus.UNAUTHORIZED, "Invalid or expired JWT: " + ex.getMessage(), "INVALID_TOKEN");
            } catch (Exception ex) {
                log.error("Authentication error: ", ex);
                return onError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "Authentication processing error", "AUTH_ERROR");
            }
        };
    }

    private boolean isPublicEndpoint(String path, HttpMethod method) {
        if (path.startsWith("/api/v1/auth/")) {
            return true;
        }
        if (path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/v3/api-docs")) {
            return true;
        }
        // Public product view and review view
        if (HttpMethod.GET.equals(method) && path.startsWith("/api/v1/products")) {
            return true;
        }
        return false;
    }

    private Claims parseClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private ServerHttpRequest mutateRequestWithClaims(ServerHttpRequest request, Claims claims) {
        String userId = claims.get("userId", String.class);
        if (userId == null) {
            userId = claims.getSubject();
        }
        String email = claims.get("email", String.class);
        String role = claims.get("role", String.class);

        ServerHttpRequest.Builder builder = request.mutate();
        if (userId != null) builder.header("X-User-Id", userId);
        if (email != null) builder.header("X-User-Email", email);
        if (role != null) builder.header("X-User-Role", role);

        return builder.build();
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status, String message, String error) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String path = exchange.getRequest().getPath().value();
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        ErrorResponse errorResponse = ErrorResponse.of(status.value(), error, message, path, traceId);

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(errorResponse);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            log.error("Error serializing response: ", e);
            byte[] fallback = ("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(fallback);
            return response.writeWith(Mono.just(buffer));
        }
    }
}
