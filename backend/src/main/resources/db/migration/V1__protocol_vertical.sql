CREATE TABLE organizations (
    id UUID PRIMARY KEY,
    slug VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE departments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(180) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, name)
);

CREATE TABLE staff_memberships (
    username VARCHAR(120) NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    department_id UUID NOT NULL,
    role_code VARCHAR(80) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    PRIMARY KEY (username, organization_id, department_id),
    FOREIGN KEY (organization_id, department_id) REFERENCES departments(organization_id, id)
);

CREATE TABLE protocol_counters (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    counter_year SMALLINT NOT NULL,
    next_value BIGINT NOT NULL CHECK (next_value > 0),
    PRIMARY KEY (organization_id, counter_year)
);

CREATE TABLE protocols (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    protocol_number VARCHAR(40) NOT NULL,
    subject VARCHAR(240) NOT NULL,
    applicant_name VARCHAR(180) NOT NULL,
    applicant_email VARCHAR(254) NOT NULL,
    initial_message TEXT NOT NULL,
    status VARCHAR(24) NOT NULL CHECK (status IN ('OPEN', 'FORWARDED', 'RECEIVED', 'DISPATCHED', 'COMPLETED')),
    assigned_department_id UUID,
    tracking_token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, protocol_number),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, assigned_department_id) REFERENCES departments(organization_id, id)
);
CREATE INDEX protocols_tenant_status_created_idx ON protocols(organization_id, status, created_at DESC);

CREATE TABLE protocol_events (
    id BIGSERIAL PRIMARY KEY,
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(40) NOT NULL,
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    department_id UUID,
    summary VARCHAR(500) NOT NULL,
    visibility VARCHAR(16) NOT NULL CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, protocol_id) REFERENCES protocols(organization_id, id),
    FOREIGN KEY (organization_id, department_id) REFERENCES departments(organization_id, id)
);
CREATE INDEX protocol_events_tenant_protocol_time_idx ON protocol_events(organization_id, protocol_id, created_at, id);

CREATE FUNCTION prevent_protocol_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'protocol events are append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER protocol_events_immutable
    BEFORE UPDATE OR DELETE ON protocol_events
    FOR EACH ROW EXECUTE FUNCTION prevent_protocol_event_mutation();
