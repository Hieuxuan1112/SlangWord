package com.slangword.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.slangword.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps attempts against the authentication endpoints so a stolen username cannot
 * be brute-forced. Login is otherwise the cheapest thing in the API to hammer:
 * every other write already needs a valid token.
 * <p>
 * Deliberately a fixed window held in memory. That is enough for a single
 * instance and keeps the dependency list short; running more than one replica
 * would need a shared store such as Redis, since each instance counts alone.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    static final String PROTECTED_PREFIX = "/api/v1/auth/";

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final int maxRequests;
    private final Duration window;

    public RateLimitFilter(ObjectMapper objectMapper, RateLimitProperties properties) {
        this.objectMapper = objectMapper;
        this.maxRequests = properties.maxRequests();
        this.window = properties.window();
    }

    private record Window(Instant startedAt, AtomicInteger count) {
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String client = clientKey(request);
        Instant now = Instant.now();

        Window current = windows.compute(client, (key, existing) ->
                existing == null || Duration.between(existing.startedAt(), now).compareTo(window) >= 0
                        ? new Window(now, new AtomicInteger(0))
                        : existing);

        if (current.count().incrementAndGet() > maxRequests) {
            long retryAfter = window.minus(Duration.between(current.startedAt(), now)).toSeconds();
            reject(response, Math.max(retryAfter, 1));
            return;
        }

        evictStaleWindows(now);
        filterChain.doFilter(request, response);
    }

    /**
     * Uses X-Forwarded-For when present because the SPA reaches the API through
     * nginx, which would otherwise make every caller look like one client.
     */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** Keeps the map from growing without bound on a long-running instance. */
    private void evictStaleWindows(Instant now) {
        if (windows.size() > 10_000) {
            windows.values().removeIf(w -> Duration.between(w.startedAt(), now).compareTo(window) >= 0);
        }
    }

    private void reject(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many authentication attempts. Try again in " + retryAfterSeconds + " seconds.");
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
