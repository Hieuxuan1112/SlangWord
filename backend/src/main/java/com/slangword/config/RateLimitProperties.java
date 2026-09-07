package com.slangword.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable because the right budget depends on deployment: behind a corporate NAT
 * many people share one address, while a public deployment wants a tighter cap.
 */
@ConfigurationProperties(prefix = "app.rate-limit.auth")
public record RateLimitProperties(int maxRequests, Duration window) {
}
