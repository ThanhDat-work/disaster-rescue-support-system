-- V6__Add_Media_Cleanup_Tasks.sql
-- Durable compensation queue for Cloudinary objects that could not be deleted.

CREATE TABLE media_cleanup_tasks (
    id BINARY(16) PRIMARY KEY,
    public_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(16) NOT NULL,
    failure_code VARCHAR(64) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 1,
    next_attempt_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_media_cleanup_public_id UNIQUE (public_id),
    CONSTRAINT media_cleanup_chk_resource_type
        CHECK (resource_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT media_cleanup_chk_attempt_count
        CHECK (attempt_count >= 1),
    CONSTRAINT media_cleanup_chk_completed_at
        CHECK (
            completed_at IS NULL
            OR completed_at >= created_at
        )
);

CREATE INDEX idx_media_cleanup_due
    ON media_cleanup_tasks(completed_at, next_attempt_at);