package com.ehapdls.diagram_generator.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token-bucket rate limiter per client IP for /api/** endpoints.
 * Configured via {@link AppProperties.RateLimit}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final int capacity;
    private final int refillTokens;
    private final long refillIntervalMs;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties appProperties) {
        AppProperties.RateLimit rl = appProperties.getRateLimit();
        this.capacity = rl.getCapacity();
        this.refillTokens = rl.getRefillTokens();
        this.refillIntervalMs = rl.getRefillDurationSeconds() * 1000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(clientIp, k -> new Bucket(capacity, refillTokens, refillIntervalMs));

        if (bucket.tryConsume()) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"Too many requests. Please try again later.\"}"
            );
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Simple token-bucket implementation (thread-safe).
     */
    private static class Bucket {
        private final int capacity;
        private final int refillTokens;
        private final long refillIntervalMs;

        private final AtomicInteger tokens;
        private volatile long lastRefillTimestamp;

        Bucket(int capacity, int refillTokens, long refillIntervalMs) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;
            if (elapsed >= refillIntervalMs) {
                long periods = elapsed / refillIntervalMs;
                int newTokens = (int) (periods * refillTokens);
                int current = tokens.get();
                tokens.set(Math.min(capacity, current + newTokens));
                lastRefillTimestamp = now;
            }
        }
    }
}
