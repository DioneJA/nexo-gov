CREATE TABLE staff_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    username VARCHAR(120) NOT NULL,
    full_name VARCHAR(180),
    position_title VARCHAR(180),
    registration_date DATE,
    registration_date_source VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (registration_date_source IN ('IMPORTED', 'UNKNOWN')),
    last_online_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, username)
);

-- Existing memberships did not retain account-registration or presence timestamps.
-- Preserve that absence explicitly instead of inventing historical values.
INSERT INTO staff_profiles (organization_id, username, registration_date_source)
SELECT DISTINCT organization_id, username, 'UNKNOWN'
  FROM staff_memberships
ON CONFLICT (organization_id, username) DO NOTHING;

CREATE INDEX staff_profiles_tenant_presence_idx
    ON staff_profiles (organization_id, last_online_at DESC NULLS LAST);
