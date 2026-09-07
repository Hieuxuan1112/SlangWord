package com.slangword.service;

import com.slangword.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Revokes a token family in a transaction of its own.
 * <p>
 * Reuse detection has to revoke the family and then reject the request. Doing both
 * in the caller's transaction means the exception that rejects the request also
 * rolls back the revocation — the security response is undone by the refusal.
 * <p>
 * {@code noRollbackFor} on the calling method is not enough: an outer
 * {@code @Transactional} (here, {@code AuthService.refresh}) owns the transaction
 * and its own rules win. {@code REQUIRES_NEW} suspends that transaction and commits
 * this one independently, so the revocation survives however the caller ends.
 * <p>
 * It lives in a separate bean because Spring's transaction advice is a proxy:
 * calling a {@code REQUIRES_NEW} method on {@code this} would bypass it and
 * silently join the existing transaction instead.
 */
@Component
public class RefreshTokenRevoker {

    private final RefreshTokenRepository repository;

    public RefreshTokenRevoker(RefreshTokenRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId, Instant now) {
        return repository.revokeFamily(familyId, now);
    }
}
