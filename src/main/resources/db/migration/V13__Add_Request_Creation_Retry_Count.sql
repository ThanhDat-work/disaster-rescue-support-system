ALTER TABLE request_create_attempts
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0;
