CREATE TABLE administrative_workflow_heads (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    subject_key VARCHAR(80) NOT NULL CHECK (subject_key ~ '^[a-z0-9][a-z0-9._-]{0,79}$'),
    subject_name VARCHAR(180) NOT NULL CHECK (btrim(subject_name) <> ''),
    current_workflow_id UUID,
    current_version INTEGER,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, subject_key),
    CHECK ((current_workflow_id IS NULL) = (current_version IS NULL))
);

CREATE TABLE administrative_workflows (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    subject_key VARCHAR(80) NOT NULL,
    subject_name VARCHAR(180) NOT NULL CHECK (btrim(subject_name) <> ''),
    version INTEGER NOT NULL CHECK (version > 0),
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id, version),
    UNIQUE (organization_id, subject_key, version),
    UNIQUE (organization_id, subject_key, id, version),
    FOREIGN KEY (organization_id, subject_key)
        REFERENCES administrative_workflow_heads (organization_id, subject_key)
);

ALTER TABLE administrative_workflow_heads
    ADD CONSTRAINT administrative_workflow_heads_current_fk
        FOREIGN KEY (organization_id, subject_key, current_workflow_id, current_version)
        REFERENCES administrative_workflows (organization_id, subject_key, id, version)
        DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE administrative_workflow_steps (
    organization_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    workflow_version INTEGER NOT NULL,
    step_order INTEGER NOT NULL CHECK (step_order > 0),
    step_name VARCHAR(180) NOT NULL CHECK (btrim(step_name) <> ''),
    PRIMARY KEY (organization_id, workflow_id, workflow_version, step_order),
    FOREIGN KEY (organization_id, workflow_id, workflow_version)
        REFERENCES administrative_workflows (organization_id, id, version)
);

CREATE TABLE administrative_workflow_step_departments (
    organization_id UUID NOT NULL,
    workflow_id UUID NOT NULL,
    workflow_version INTEGER NOT NULL,
    step_order INTEGER NOT NULL,
    department_id UUID NOT NULL,
    PRIMARY KEY (organization_id, workflow_id, workflow_version, step_order, department_id),
    FOREIGN KEY (organization_id, workflow_id, workflow_version, step_order)
        REFERENCES administrative_workflow_steps (organization_id, workflow_id, workflow_version, step_order),
    FOREIGN KEY (organization_id, department_id)
        REFERENCES departments (organization_id, id)
);

CREATE TABLE administrative_processes (
    id UUID NOT NULL,
    organization_id UUID NOT NULL,
    workflow_subject_key VARCHAR(80) NOT NULL,
    workflow_id UUID NOT NULL,
    workflow_version INTEGER NOT NULL,
    title VARCHAR(240) NOT NULL CHECK (btrim(title) <> ''),
    current_step_order INTEGER NOT NULL CHECK (current_step_order > 0),
    current_department_id UUID NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE')),
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    FOREIGN KEY (organization_id, workflow_subject_key, workflow_id, workflow_version)
        REFERENCES administrative_workflows (organization_id, subject_key, id, version)
);

CREATE TABLE administrative_process_workflow_steps (
    organization_id UUID NOT NULL,
    process_id UUID NOT NULL,
    step_order INTEGER NOT NULL CHECK (step_order > 0),
    step_name VARCHAR(180) NOT NULL CHECK (btrim(step_name) <> ''),
    PRIMARY KEY (organization_id, process_id, step_order),
    FOREIGN KEY (organization_id, process_id)
        REFERENCES administrative_processes (organization_id, id)
);

CREATE TABLE administrative_process_step_departments (
    organization_id UUID NOT NULL,
    process_id UUID NOT NULL,
    step_order INTEGER NOT NULL,
    department_id UUID NOT NULL,
    PRIMARY KEY (organization_id, process_id, step_order, department_id),
    FOREIGN KEY (organization_id, process_id, step_order)
        REFERENCES administrative_process_workflow_steps (organization_id, process_id, step_order),
    FOREIGN KEY (organization_id, department_id)
        REFERENCES departments (organization_id, id)
);

ALTER TABLE administrative_processes
    ADD CONSTRAINT administrative_processes_current_stage_fk
        FOREIGN KEY (organization_id, id, current_step_order, current_department_id)
        REFERENCES administrative_process_step_departments (organization_id, process_id, step_order, department_id)
        DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE administrative_process_events (
    id BIGSERIAL PRIMARY KEY,
    organization_id UUID NOT NULL,
    process_id UUID NOT NULL,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('OPENED', 'ADVANCED')),
    from_step_order INTEGER,
    to_step_order INTEGER NOT NULL CHECK (to_step_order > 0),
    from_department_id UUID,
    to_department_id UUID NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((action = 'OPENED' AND from_step_order IS NULL AND from_department_id IS NULL)
        OR (action = 'ADVANCED' AND from_step_order IS NOT NULL AND from_department_id IS NOT NULL)),
    FOREIGN KEY (organization_id, process_id)
        REFERENCES administrative_processes (organization_id, id),
    FOREIGN KEY (organization_id, from_department_id)
        REFERENCES departments (organization_id, id),
    FOREIGN KEY (organization_id, to_department_id)
        REFERENCES departments (organization_id, id)
);

CREATE INDEX administrative_processes_tenant_subject_idx
    ON administrative_processes (organization_id, workflow_subject_key, created_at DESC);
CREATE INDEX administrative_process_events_tenant_process_idx
    ON administrative_process_events (organization_id, process_id, id);

CREATE FUNCTION prevent_administrative_workflow_definition_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'administrative workflow versions are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER administrative_workflows_immutable
    BEFORE UPDATE OR DELETE ON administrative_workflows
    FOR EACH ROW EXECUTE FUNCTION prevent_administrative_workflow_definition_mutation();
CREATE TRIGGER administrative_workflow_steps_immutable
    BEFORE UPDATE OR DELETE ON administrative_workflow_steps
    FOR EACH ROW EXECUTE FUNCTION prevent_administrative_workflow_definition_mutation();
CREATE TRIGGER administrative_workflow_step_departments_immutable
    BEFORE UPDATE OR DELETE ON administrative_workflow_step_departments
    FOR EACH ROW EXECUTE FUNCTION prevent_administrative_workflow_definition_mutation();
CREATE TRIGGER administrative_workflows_no_truncate
    BEFORE TRUNCATE ON administrative_workflows
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_administrative_workflow_definition_mutation();
CREATE TRIGGER administrative_workflow_steps_no_truncate
    BEFORE TRUNCATE ON administrative_workflow_steps
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_administrative_workflow_definition_mutation();
CREATE TRIGGER administrative_workflow_step_departments_no_truncate
    BEFORE TRUNCATE ON administrative_workflow_step_departments
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_administrative_workflow_definition_mutation();

CREATE FUNCTION prevent_administrative_process_snapshot_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'administrative process workflow snapshots are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER administrative_process_workflow_steps_immutable
    BEFORE UPDATE OR DELETE ON administrative_process_workflow_steps
    FOR EACH ROW EXECUTE FUNCTION prevent_administrative_process_snapshot_mutation();
CREATE TRIGGER administrative_process_step_departments_immutable
    BEFORE UPDATE OR DELETE ON administrative_process_step_departments
    FOR EACH ROW EXECUTE FUNCTION prevent_administrative_process_snapshot_mutation();
CREATE TRIGGER administrative_process_workflow_steps_no_truncate
    BEFORE TRUNCATE ON administrative_process_workflow_steps
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_administrative_process_snapshot_mutation();
CREATE TRIGGER administrative_process_step_departments_no_truncate
    BEFORE TRUNCATE ON administrative_process_step_departments
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_administrative_process_snapshot_mutation();

CREATE FUNCTION prevent_administrative_process_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'administrative process events are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER administrative_process_events_immutable
    BEFORE UPDATE OR DELETE ON administrative_process_events
    FOR EACH ROW EXECUTE FUNCTION prevent_administrative_process_event_mutation();
CREATE TRIGGER administrative_process_events_no_truncate
    BEFORE TRUNCATE ON administrative_process_events
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_administrative_process_event_mutation();
