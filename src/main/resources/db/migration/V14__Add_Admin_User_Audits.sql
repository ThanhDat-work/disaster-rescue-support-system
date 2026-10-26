CREATE TABLE admin_user_audits (
    id BINARY(16) NOT NULL PRIMARY KEY,
    actor_user_id BINARY(16) NOT NULL,
    target_user_id BINARY(16) NOT NULL,
    action VARCHAR(32) NOT NULL,
    before_value VARCHAR(255) NOT NULL,
    after_value VARCHAR(255) NOT NULL,
    request_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_admin_user_audits_actor FOREIGN KEY (actor_user_id)
        REFERENCES users(id),
    CONSTRAINT fk_admin_user_audits_target FOREIGN KEY (target_user_id)
        REFERENCES users(id),
    CONSTRAINT chk_admin_user_audit_action CHECK (
        action IN ('STATUS_CHANGED', 'ROLES_CHANGED')
    ),
    INDEX idx_admin_user_audits_target_time (target_user_id, created_at),
    INDEX idx_admin_user_audits_request (request_id)
);
