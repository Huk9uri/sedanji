-- Refuse ambiguous data rather than merge existing verified relationships.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM mentions GROUP BY chef_id, restaurant_id, video_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'V3 blocked: duplicate chef/restaurant/video relationships require explicit resolution';
    END IF;
END $$;

ALTER TABLE chefs DROP CONSTRAINT chefs_name_key;
CREATE INDEX chefs_name_idx ON chefs(name);
ALTER TABLE chefs ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE chefs ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE restaurants ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE restaurants ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE restaurants ALTER COLUMN road_address DROP NOT NULL;
ALTER TABLE restaurants ALTER COLUMN address DROP NOT NULL;

ALTER TABLE mentions DROP CONSTRAINT mentions_chef_id_restaurant_id_video_id_mention_type_key;
ALTER TABLE mentions ADD CONSTRAINT mentions_relationship_key UNIQUE (chef_id, restaurant_id, video_id);
ALTER TABLE mentions ALTER COLUMN evidence DROP NOT NULL;
ALTER TABLE mentions DROP CONSTRAINT mentions_check1;
ALTER TABLE mentions ADD CONSTRAINT mentions_approval_check CHECK (
    verification_status <> 'APPROVED' OR
    (mention_type IS NOT NULL AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
);
ALTER TABLE mentions ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);
ALTER TABLE mentions ADD COLUMN publication_status VARCHAR(20);
UPDATE mentions SET publication_status = CASE WHEN verification_status='APPROVED' THEN 'PUBLISHED' ELSE 'SUSPENDED' END;
ALTER TABLE mentions ALTER COLUMN publication_status SET NOT NULL;
ALTER TABLE mentions ALTER COLUMN publication_status SET DEFAULT 'SUSPENDED';
ALTER TABLE mentions ADD CONSTRAINT mentions_publication_check CHECK (
    publication_status IN ('PUBLISHED','SUSPENDED') AND
    (publication_status <> 'PUBLISHED' OR verification_status='APPROVED')
);
CREATE INDEX mentions_public_idx ON mentions(restaurant_id) WHERE verification_status='APPROVED' AND publication_status='PUBLISHED';

CREATE TABLE mention_drafts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id),
    chef_id BIGINT REFERENCES chefs(id),
    video_id VARCHAR(11) REFERENCES videos(id),
    candidate_id BIGINT REFERENCES restaurant_candidates(id),
    target_mention_id BIGINT REFERENCES mentions(id),
    applied_mention_id BIGINT REFERENCES mentions(id),
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CREATE','CORRECTION')),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPLIED')),
    mention_type VARCHAR(20) CHECK (mention_type IN ('REGULAR','RECOMMENDED','VISITED','MENTIONED')),
    timestamp_seconds INTEGER CHECK (timestamp_seconds >= 0),
    verification_note TEXT,
    change_reason TEXT,
    base_version BIGINT CHECK (base_version >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by BIGINT NOT NULL REFERENCES admin_users(id),
    updated_by BIGINT NOT NULL REFERENCES admin_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((kind='CREATE' AND target_mention_id IS NULL AND base_version IS NULL) OR
           (kind='CORRECTION' AND target_mention_id IS NOT NULL AND base_version IS NOT NULL
            AND change_reason IS NOT NULL AND length(trim(change_reason)) > 0)),
    CHECK ((status='DRAFT' AND applied_mention_id IS NULL) OR
           (status='APPLIED' AND applied_mention_id IS NOT NULL AND chef_id IS NOT NULL
            AND video_id IS NOT NULL AND mention_type IS NOT NULL))
);
CREATE INDEX mention_drafts_restaurant_idx ON mention_drafts(restaurant_id,status,updated_at DESC);
CREATE UNIQUE INDEX mention_drafts_open_correction_key ON mention_drafts(target_mention_id)
    WHERE kind='CORRECTION' AND status='DRAFT';

CREATE TABLE admin_audit_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id BIGINT NOT NULL REFERENCES admin_users(id),
    mention_id BIGINT REFERENCES mentions(id),
    draft_id BIGINT REFERENCES mention_drafts(id),
    restaurant_id BIGINT REFERENCES restaurants(id),
    chef_id BIGINT REFERENCES chefs(id),
    action VARCHAR(50) NOT NULL CHECK (length(trim(action)) > 0),
    before_value JSONB,
    after_value JSONB,
    reason TEXT,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (num_nonnulls(mention_id,draft_id,restaurant_id,chef_id) >= 1)
);
CREATE INDEX admin_audit_mention_idx ON admin_audit_events(mention_id,occurred_at DESC);
CREATE INDEX admin_audit_restaurant_idx ON admin_audit_events(restaurant_id,occurred_at DESC);
CREATE FUNCTION reject_admin_audit_changes() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Admin audit events are append-only'; END $$;
CREATE TRIGGER admin_audit_append_only BEFORE UPDATE OR DELETE ON admin_audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_admin_audit_changes();

CREATE TABLE admin_request_receipts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    admin_id BIGINT NOT NULL REFERENCES admin_users(id),
    idempotency_key VARCHAR(128) NOT NULL CHECK (length(trim(idempotency_key)) > 0),
    request_path VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    response_status INTEGER NOT NULL CHECK (response_status BETWEEN 200 AND 299),
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    UNIQUE(admin_id,idempotency_key),
    CHECK (expires_at > created_at)
);
CREATE INDEX admin_request_receipts_expiry_idx ON admin_request_receipts(expires_at);
