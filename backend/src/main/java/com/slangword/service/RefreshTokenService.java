package com.slangword.service;

import com.slangword.config.JwtProperties;
import com.slangword.domain.RefreshToken;
import com.slangword.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues, rotates and revokes refresh tokens.
 * <p>
 * The access token stays a stateless JWT because verifying it must not touch the
 * database — that is the whole point of the design. The cost of statelessness is
 * that it cannot be withdrawn, so it is short-lived, and the long-lived
 * credential is this refresh token, which is stored and therefore revocable.
 * <p>
 * Rotation with reuse detection: each refresh returns a new token and marks the
 * old one used. Presenting an already-used token means either it was stolen and
 * replayed, or a client is out of sync; both are answered by revoking the entire
 * family, forcing a fresh login.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32; // 256 bits

    private final RefreshTokenRepository repository;
    private final RefreshTokenRevoker revoker;
    private final SecureRandom random = new SecureRandom();
    private final long lifetimeSeconds;

    public RefreshTokenService(RefreshTokenRepository repository,
                               RefreshTokenRevoker revoker,
                               JwtProperties properties) {
        this.repository = repository;
        this.revoker = revoker;
        this.lifetimeSeconds = properties.refreshExpirationSeconds();
    }

    /** The raw token goes to the client exactly once; only its hash is persisted. */
    public record IssuedToken(String token, Instant expiresAt) {
    }

    @Transactional
    public IssuedToken issue(Long userId) {
        return issue(userId, UUID.randomUUID());
    }

    private IssuedToken issue(Long userId, UUID familyId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plusSeconds(lifetimeSeconds);
        repository.save(new RefreshToken(userId, hash(token), familyId, expiresAt));
        return new IssuedToken(token, expiresAt);
    }

    /**
     * Exchanges a refresh token for a new pair. Returns the user id the caller
     * proved ownership of, together with the replacement token.
     * <p>
     * The reuse branch delegates revocation to {@link RefreshTokenRevoker}, which
     * commits in a transaction of its own — see the reasoning there.
     */
    @Transactional
    public Rotation rotate(String presentedToken) {
        Instant now = Instant.now();
        RefreshToken stored = repository.findByTokenHash(hash(presentedToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!stored.isUsable(now)) {
            // Replay or a desynchronised client: distrust the whole family.
            int revoked = revoker.revokeFamily(stored.getFamilyId(), now);
            log.warn("Refresh token reuse detected for user {}; revoked {} token(s) in family {}",
                    stored.getUserId(), revoked, stored.getFamilyId());
            throw new BadCredentialsException("Invalid refresh token");
        }

        stored.markUsed(now);
        IssuedToken replacement = issue(stored.getUserId(), stored.getFamilyId());
        return new Rotation(stored.getUserId(), replacement);
    }

    public record Rotation(Long userId, IssuedToken token) {
    }

    /** Logout: ends this session only, leaving the user's other devices signed in. */
    @Transactional
    public void revoke(String presentedToken) {
        Instant now = Instant.now();
        Optional<RefreshToken> stored = repository.findByTokenHash(hash(presentedToken));
        stored.ifPresent(token -> repository.revokeFamily(token.getFamilyId(), now));
        // Normal logout: no exception follows, so the caller's transaction commits it.
    }

    @Transactional
    public int revokeAllForUser(Long userId) {
        return repository.revokeAllForUser(userId, Instant.now());
    }

    @Transactional
    public int purgeExpired() {
        return repository.deleteExpiredBefore(Instant.now());
    }

    /**
     * SHA-256, not BCrypt. The token is 256 random bits, so there is no dictionary
     * to attack and nothing for a slow hash to buy; lookup by hash also has to be
     * a single indexed read, which a salted BCrypt hash cannot provide.
     */
    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by every JVM", ex);
        }
    }
}
