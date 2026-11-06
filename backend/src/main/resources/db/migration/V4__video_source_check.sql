ALTER TABLE videos
    ADD COLUMN source_check_status VARCHAR(20) NOT NULL DEFAULT 'UNCHECKED',
    ADD COLUMN source_checked_at TIMESTAMPTZ,
    ADD COLUMN source_check_reason VARCHAR(20),
    ADD CONSTRAINT videos_source_check CHECK (
        (source_check_status = 'UNCHECKED' AND source_checked_at IS NULL AND source_check_reason IS NULL)
        OR (source_check_status = 'AVAILABLE' AND source_checked_at IS NOT NULL AND source_check_reason IS NULL)
        OR (source_check_status = 'REVIEW_REQUIRED' AND source_checked_at IS NOT NULL
            AND source_check_reason IS NOT NULL AND source_check_reason = 'UNAVAILABLE')
    );
