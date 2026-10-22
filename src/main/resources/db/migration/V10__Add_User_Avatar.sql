ALTER TABLE users
    ADD COLUMN avatar_public_id VARCHAR(255) NULL,
    ADD COLUMN avatar_secure_url VARCHAR(500) NULL;

ALTER TABLE users
    ADD CONSTRAINT users_chk_avatar_pair CHECK (
        (avatar_public_id IS NULL AND avatar_secure_url IS NULL)
        OR (avatar_public_id IS NOT NULL AND avatar_secure_url IS NOT NULL)
    );

CREATE INDEX idx_req_owner_created
    ON rescue_requests(user_id, created_at);
CREATE INDEX idx_history_actor_time
    ON status_histories(changed_by_user_id, actor_role, created_at, new_status);
CREATE INDEX idx_role_switch_outcome_time
    ON role_switch_audits(user_id, outcome, created_at);