-- V3__Add_Authentication_Support.sql
-- Bổ sung persistence foundation cho OTP, JWT revocation version và rate limiting.

ALTER TABLE users
    ADD COLUMN auth_version INT NOT NULL DEFAULT 0 AFTER status;

CREATE TABLE verification_tokens (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    purpose VARCHAR(32) NOT NULL,
    token_digest CHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    failed_attempts INT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts INT UNSIGNED NOT NULL DEFAULT 5,
    verified_at DATETIME(6),
    consumed_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_verification_token_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT verification_tokens_chk_purpose
        CHECK (purpose IN ('ACCOUNT_ACTIVATION', 'PASSWORD_RESET')),

    CONSTRAINT verification_tokens_chk_attempts
        CHECK (failed_attempts <= max_attempts),

    CONSTRAINT verification_tokens_chk_expiry
        CHECK (expires_at > created_at),

    CONSTRAINT verification_tokens_chk_verified_at
        CHECK (verified_at IS NULL OR verified_at >= created_at),

    CONSTRAINT verification_tokens_chk_consumed_at
        CHECK (consumed_at IS NULL OR consumed_at >= created_at),

    CONSTRAINT verification_tokens_chk_updated_at
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_verification_lookup
    ON verification_tokens(user_id, purpose, consumed_at, expires_at);

CREATE INDEX idx_verification_cleanup
    ON verification_tokens(expires_at, consumed_at);

CREATE TABLE auth_rate_limits (
    id BINARY(16) PRIMARY KEY,
    action VARCHAR(32) NOT NULL,
    scope VARCHAR(32) NOT NULL,
    subject_key CHAR(64) NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    penalty_level INT UNSIGNED NOT NULL DEFAULT 0,
    window_started_at DATETIME(6) NOT NULL,
    blocked_until DATETIME(6),
    last_attempt_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT uk_rate_action_scope_subject
        UNIQUE (action, scope, subject_key),

    CONSTRAINT auth_rate_limits_chk_action
        CHECK (
            action IN (
                'REGISTER',
                'RESEND_OTP',
                'VERIFY_ACTIVATION',
                'LOGIN',
                'FORGOT_PASSWORD',
                'VERIFY_RESET'
            )
        ),

    CONSTRAINT auth_rate_limits_chk_scope
        CHECK (scope IN ('EMAIL_IP', 'IP')),

    CONSTRAINT auth_rate_limits_chk_window
        CHECK (last_attempt_at >= window_started_at),

    CONSTRAINT auth_rate_limits_chk_blocked_until
        CHECK (
            blocked_until IS NULL
            OR blocked_until >= window_started_at
        ),

    CONSTRAINT auth_rate_limits_chk_updated_at
        CHECK (updated_at >= created_at)
);

CREATE INDEX idx_rate_limit_blocked_until
    ON auth_rate_limits(blocked_until);

CREATE INDEX idx_rate_limit_last_attempt
    ON auth_rate_limits(last_attempt_at);