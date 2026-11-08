-- Organization-wide access is an independent control from global accounts and
-- department/organization memberships. A missing row preserves current access.
CREATE TABLE organization_member_access_status (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    username VARCHAR(120) NOT NULL CHECK (btrim(username) <> ''),
    access_active BOOLEAN NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, username)
);

CREATE INDEX organization_member_access_status_suspended_idx
    ON organization_member_access_status (organization_id, username)
    WHERE access_active = false;
