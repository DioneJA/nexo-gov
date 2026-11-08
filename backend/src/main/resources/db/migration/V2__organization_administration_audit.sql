CREATE TABLE organization_memberships (
    username VARCHAR(120) NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    role_code VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (username, organization_id, role_code)
);

DO $$
DECLARE conflicting_names TEXT;
BEGIN
    SELECT string_agg(format('%s: %s', organization_id, names), E'\n')
      INTO conflicting_names
      FROM (
          SELECT organization_id,
                 string_agg(format('%s (%s)', name, ids), '; ' ORDER BY lower(btrim(name))) AS names
            FROM (
                SELECT organization_id, lower(btrim(name)) AS normalized_name,
                       string_agg(name, ', ' ORDER BY name) AS name,
                       string_agg(id::text, ', ' ORDER BY id) AS ids
                  FROM departments
                 GROUP BY organization_id, lower(btrim(name))
                HAVING count(*) > 1
            ) duplicate_groups
           GROUP BY organization_id
      ) duplicates;

    IF conflicting_names IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot enforce case-insensitive department name uniqueness: existing duplicates require manual reconciliation.'
            USING DETAIL = conflicting_names,
                  HINT = 'Rename or merge duplicate department records, then rerun the migration. No records were changed.';
    END IF;
END;
$$;

CREATE UNIQUE INDEX departments_organization_name_case_insensitive_idx
    ON departments (organization_id, lower(btrim(name)));

CREATE TABLE organization_audit_events (
    id BIGSERIAL PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    actor_username VARCHAR(120) NOT NULL,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    summary VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX organization_audit_events_tenant_time_idx
    ON organization_audit_events (organization_id, created_at DESC, id DESC);

CREATE FUNCTION prevent_organization_audit_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'organization audit events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER organization_audit_events_immutable
    BEFORE UPDATE OR DELETE ON organization_audit_events
    FOR EACH ROW EXECUTE FUNCTION prevent_organization_audit_event_mutation();

CREATE TRIGGER organization_audit_events_no_truncate
    BEFORE TRUNCATE ON organization_audit_events
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_organization_audit_event_mutation();
