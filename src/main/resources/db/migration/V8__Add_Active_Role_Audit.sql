-- T3-09 active-role authentication and durable role-switch audit.

ALTER TABLE auth_rate_limits
    DROP CHECK auth_rate_limits_chk_action;

ALTER TABLE auth_rate_limits
    ADD CONSTRAINT auth_rate_limits_chk_action
        CHECK (
            action IN (
                'REGISTER',
                'RESEND_OTP',
                'VERIFY_ACTIVATION',
                'LOGIN',
                'FORGOT_PASSWORD',
                'VERIFY_RESET',
                'ROLE_SWITCH'
            )
        );

CREATE TABLE role_switch_audits (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    from_role VARCHAR(50),
    to_role VARCHAR(50),
    outcome VARCHAR(16) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    source_ip VARCHAR(45) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT fk_role_switch_audit_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT role_switch_audits_chk_outcome
        CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_role_switch_user_time
    ON role_switch_audits(user_id, created_at);

-- Thu hồi toàn bộ JWT cũ có thể đang chứa nhiều authorities.
UPDATE users
SET auth_version = auth_version + 1;