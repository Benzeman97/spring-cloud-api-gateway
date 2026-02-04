package com.jibra.apigateway.controller;

import com.jibra.apigateway.util.ServerWebExchangeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/fallback")
public class FallbackController {

    private static final Logger LOGGER = LogManager.getLogger(FallbackController.class);

    @RequestMapping("/payment")
    public Mono<ResponseEntity<Map<String, Object>>> paymentFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "payment-service", "PAYMENT_SERVICE");
    }

    @RequestMapping("/user")
    public Mono<ResponseEntity<Map<String, Object>>> userFallback(ServerWebExchange exchange) {
        return buildFallbackResponse(exchange, "user-service", "USER_SERVICE");
    }

    private Mono<ResponseEntity<Map<String, Object>>> buildFallbackResponse(
            ServerWebExchange exchange,
            String serviceName,
            String serviceCode) {

        // Extract request details for logging
        String requestId = getOrGenerateRequestId(exchange);
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        // Get the exception that triggered the circuit breaker
        Throwable exception = exchange.getAttribute(
                ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR
        );

        // Log the failure with context
        if (exception != null) {

            Throwable rootCause = getRootCause(exception);

            LOGGER.error(
                    "Circuit breaker activated for {} | RequestId: {} | Method: {} | Path: {} | Reason: {}",
                    serviceName, requestId, method, path, rootCause.getMessage(),
                    exception  // Include full stack trace for debugging
                     );
        } else {
            LOGGER.warn("Circuit breaker activated for {} | RequestId: {} | Method: {} | Path: {} | Reason: Unknown",
                    serviceName, requestId, method, path);
        }

        // Build response payload
        Map<String, Object> response = new HashMap<>();
        response.put("timestamp", LocalDateTime.now(ZoneOffset.UTC).toString());
        response.put("status", HttpStatus.SERVICE_UNAVAILABLE.value());
        response.put("error", "Service Unavailable");
        response.put("message", String.format(
                "%s is currently unavailable. Please try again later.",
                serviceName
        ));
        response.put("errorCode", serviceCode + "_UNAVAILABLE");
        response.put("path", path);
        response.put("requestId", requestId);

        // Optional: Add retry-after header
        return Mono.just(
                ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("Retry-After", "10") // Suggest retry after 10 seconds
                        .header("X-Request-Id", requestId)
                        .body(response)
        );
    }

    /**
     * Extracts or generates a unique request ID for tracing
     */
    private String getOrGenerateRequestId(ServerWebExchange exchange) {
        // Try to get existing trace ID from headers
        String traceId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        if (traceId == null || traceId.isEmpty()) {
            traceId = exchange.getRequest().getHeaders().getFirst("X-Trace-Id");
        }
        if (traceId == null || traceId.isEmpty()) {
            traceId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
        }

        // Generate new ID if none exists
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }

    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

}
