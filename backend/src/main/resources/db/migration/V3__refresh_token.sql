-- Refresh tokens are stored so that a session can be revoked. An access token is
-- self-contained and cannot be withdrawn before it expires, which is why it is
-- now short-lived (15 minutes) and the long-lived credential lives here instead.
--
-- Only a SHA-256 hash of the token is kept: a database leak then exposes no
-- usable credential, the same reasoning that applies to password storage. BCrypt
-- is unnecessary here because the token is already 256 bits of entropy, so there
-- is no dictionary to attack and no need for a deliberately slow hash.
create table refresh_token (
    id          bigserial primary key,
    user_id     bigint      not null references app_user (id) on delete cascade,
    token_hash  varchar(64) not null unique,
    -- Tokens issued from the same login share a family id. If a token that has
    -- already been used is presented again, the whole family is revoked: either
    -- it was stolen and replayed, or the legitimate client is out of sync, and
    -- neither case should be allowed to continue.
    family_id   uuid        not null,
    expires_at  timestamptz not null,
    revoked_at  timestamptz,
    used_at     timestamptz,
    created_at  timestamptz not null
);

create index idx_refresh_token_user on refresh_token (user_id);
create index idx_refresh_token_family on refresh_token (family_id);
create index idx_refresh_token_expiry on refresh_token (expires_at);
