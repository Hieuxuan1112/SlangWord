package com.slangword.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * HMAC-SHA256 needs a key of at least 32 bytes; {@code Keys.hmacShaKeyFor} throws
 * otherwise. Validating here turns that into a startup failure naming the property,
 * instead of an opaque exception on the first login attempt.
 *
 * @param expirationSeconds        access token lifetime. Short, because a JWT cannot
 *                                 be revoked before it expires.
 * @param refreshExpirationSeconds refresh token lifetime. Long, because that token
 *                                 is stored server-side and can be revoked.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank(message = "set the APP_JWT_SECRET environment variable")
        @Size(min = 32, message = "set the APP_JWT_SECRET environment variable to a random "
                + "string of at least 32 characters (generate one with: openssl rand -base64 48)")
        String secret,

        @Min(value = 60, message = "app.jwt.expiration-seconds must be at least 60")
        long expirationSeconds,

        @Min(value = 300, message = "app.jwt.refresh-expiration-seconds must be at least 300")
        long refreshExpirationSeconds) {
}
