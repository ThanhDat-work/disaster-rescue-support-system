CREATE TABLE request_create_attempts (
    id BINARY(16) PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    operation_key BINARY(16) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    state VARCHAR(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    request_id BINARY(16),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_create_attempt_owner_key UNIQUE (user_id, operation_key),
    CONSTRAINT fk_create_attempt_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_create_attempt_request FOREIGN KEY (request_id) REFERENCES rescue_requests(id),
    CONSTRAINT chk_create_attempt_state CHECK (state IN ('PROCESSING','SUCCEEDED','FAILED','UNKNOWN'))
);
CREATE INDEX idx_create_attempt_user_started
    ON request_create_attempts(user_id, started_at);
