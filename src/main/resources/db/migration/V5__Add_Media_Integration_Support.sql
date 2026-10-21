-- V5__Add_Media_Integration_Support.sql
-- Add image/video metadata and retryable Cloudinary cleanup state.

ALTER TABLE media_assets
    ADD COLUMN resource_type VARCHAR(16) NOT NULL DEFAULT 'IMAGE'
        AFTER secure_url,
    ADD COLUMN mime_type VARCHAR(100) NOT NULL DEFAULT 'image/jpeg'
        AFTER resource_type,
    ADD COLUMN duration_seconds DECIMAL(10,3)
        AFTER height,
    ADD COLUMN cloud_deleted_at DATETIME(6)
        AFTER deleted_at,
    ADD CONSTRAINT media_assets_chk_resource_type
        CHECK (resource_type IN ('IMAGE', 'VIDEO')),
    ADD CONSTRAINT media_assets_chk_mime_type
        CHECK (
            (resource_type = 'IMAGE'
                AND mime_type IN (
                    'image/jpeg',
                    'image/png',
                    'image/webp'
                ))
            OR
            (resource_type = 'VIDEO'
                AND mime_type = 'video/mp4')
        ),
    ADD CONSTRAINT media_assets_chk_duration
        CHECK (
            duration_seconds IS NULL
            OR duration_seconds >= 0
        ),
    ADD CONSTRAINT media_assets_chk_cloud_deleted_at
        CHECK (
            cloud_deleted_at IS NULL
            OR (
                deleted_at IS NOT NULL
                AND cloud_deleted_at >= deleted_at
            )
        );

CREATE INDEX idx_media_cleanup
    ON media_assets(deleted_at, cloud_deleted_at);