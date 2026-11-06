-- Expand without inventing baseline snapshots for legacy CORRECTION rows.
ALTER TABLE mention_drafts ADD COLUMN base_snapshot JSONB;
ALTER TABLE mention_drafts ADD COLUMN proposed_values JSONB;
ALTER TABLE mention_drafts ADD COLUMN changed_fields JSONB;
ALTER TABLE mention_drafts ADD COLUMN reference_snapshot JSONB;
ALTER TABLE mention_drafts ADD COLUMN applied_by BIGINT REFERENCES admin_users(id);
ALTER TABLE mention_drafts ADD COLUMN applied_at TIMESTAMPTZ;
ALTER TABLE mention_drafts ADD CONSTRAINT correction_snapshot_shape CHECK (
  kind <> 'CORRECTION' OR base_snapshot IS NULL OR
  (proposed_values IS NOT NULL AND changed_fields IS NOT NULL AND reference_snapshot IS NOT NULL
   AND jsonb_typeof(base_snapshot)='object' AND jsonb_typeof(proposed_values)='object'
   AND jsonb_typeof(changed_fields)='array' AND jsonb_typeof(reference_snapshot)='object'
   AND length(trim(change_reason)) BETWEEN 1 AND 2000
   AND ((status='DRAFT' AND applied_by IS NULL AND applied_at IS NULL)
     OR (status='APPLIED' AND applied_by IS NOT NULL AND applied_at IS NOT NULL))));
CREATE INDEX mention_corrections_list_idx ON mention_drafts(target_mention_id,updated_at DESC,id DESC) WHERE kind='CORRECTION';

CREATE TABLE restaurant_revisions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  target_id BIGINT NOT NULL REFERENCES restaurants(id),
  base_version BIGINT NOT NULL CHECK (base_version >= 0),
  base_snapshot JSONB NOT NULL CHECK (jsonb_typeof(base_snapshot)='object'),
  proposed_values JSONB NOT NULL CHECK (jsonb_typeof(proposed_values)='object'),
  changed_fields JSONB NOT NULL CHECK (jsonb_typeof(changed_fields)='array'),
  reference_snapshot JSONB NOT NULL CHECK (jsonb_typeof(reference_snapshot)='object'),
  change_reason TEXT NOT NULL CHECK (length(trim(change_reason)) BETWEEN 1 AND 2000),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPLIED')),
  version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
  created_by BIGINT NOT NULL REFERENCES admin_users(id),
  updated_by BIGINT NOT NULL REFERENCES admin_users(id),
  applied_by BIGINT REFERENCES admin_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  applied_at TIMESTAMPTZ,
  CHECK ((status='DRAFT' AND applied_by IS NULL AND applied_at IS NULL) OR
         (status='APPLIED' AND applied_by IS NOT NULL AND applied_at IS NOT NULL))
);
CREATE UNIQUE INDEX restaurant_revisions_open_key ON restaurant_revisions(target_id) WHERE status='DRAFT';
CREATE INDEX restaurant_revisions_list_idx ON restaurant_revisions(target_id,updated_at DESC,id DESC);

CREATE TABLE chef_revisions (
  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  target_id BIGINT NOT NULL REFERENCES chefs(id),
  base_version BIGINT NOT NULL CHECK (base_version >= 0),
  base_snapshot JSONB NOT NULL CHECK (jsonb_typeof(base_snapshot)='object'),
  proposed_values JSONB NOT NULL CHECK (jsonb_typeof(proposed_values)='object'),
  changed_fields JSONB NOT NULL CHECK (jsonb_typeof(changed_fields)='array'),
  reference_snapshot JSONB NOT NULL CHECK (jsonb_typeof(reference_snapshot)='object'),
  change_reason TEXT NOT NULL CHECK (length(trim(change_reason)) BETWEEN 1 AND 2000),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','APPLIED')),
  version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
  created_by BIGINT NOT NULL REFERENCES admin_users(id),
  updated_by BIGINT NOT NULL REFERENCES admin_users(id),
  applied_by BIGINT REFERENCES admin_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  applied_at TIMESTAMPTZ,
  CHECK ((status='DRAFT' AND applied_by IS NULL AND applied_at IS NULL) OR
         (status='APPLIED' AND applied_by IS NOT NULL AND applied_at IS NOT NULL))
);
CREATE UNIQUE INDEX chef_revisions_open_key ON chef_revisions(target_id) WHERE status='DRAFT';
CREATE INDEX chef_revisions_list_idx ON chef_revisions(target_id,updated_at DESC,id DESC);
ALTER TABLE admin_audit_events ADD COLUMN restaurant_revision_id BIGINT REFERENCES restaurant_revisions(id);
ALTER TABLE admin_audit_events ADD COLUMN chef_revision_id BIGINT REFERENCES chef_revisions(id);
CREATE INDEX admin_audit_chef_history_idx ON admin_audit_events(chef_id,occurred_at DESC,id DESC);
CREATE INDEX admin_audit_restaurant_history_idx ON admin_audit_events(restaurant_id,occurred_at DESC,id DESC);
CREATE INDEX admin_audit_mention_history_idx ON admin_audit_events(mention_id,occurred_at DESC,id DESC);
