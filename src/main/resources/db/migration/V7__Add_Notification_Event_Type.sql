ALTER TABLE notifications
    ADD COLUMN event_type VARCHAR(64) NULL AFTER message;

UPDATE notifications
SET event_type = 'LEGACY'
WHERE event_type IS NULL;

ALTER TABLE notifications
    MODIFY COLUMN event_type VARCHAR(64) NOT NULL;

CREATE INDEX idx_notify_recipient_created
    ON notifications(recipient_user_id, created_at);