ALTER TABLE administrative_workflow_step_departments
    ADD COLUMN department_name VARCHAR(180);

ALTER TABLE administrative_workflow_step_departments
    DISABLE TRIGGER administrative_workflow_step_departments_immutable;
UPDATE administrative_workflow_step_departments snapshot
   SET department_name = department.name
  FROM departments department
 WHERE department.organization_id = snapshot.organization_id
   AND department.id = snapshot.department_id;
ALTER TABLE administrative_workflow_step_departments
    ENABLE TRIGGER administrative_workflow_step_departments_immutable;

ALTER TABLE administrative_workflow_step_departments
    ALTER COLUMN department_name SET NOT NULL,
    ADD CONSTRAINT administrative_workflow_step_departments_name_nonblank
        CHECK (btrim(department_name) <> '');

ALTER TABLE administrative_process_step_departments
    ADD COLUMN department_name VARCHAR(180);

ALTER TABLE administrative_process_step_departments
    DISABLE TRIGGER administrative_process_step_departments_immutable;
UPDATE administrative_process_step_departments snapshot
   SET department_name = department.name
  FROM departments department
 WHERE department.organization_id = snapshot.organization_id
   AND department.id = snapshot.department_id;
ALTER TABLE administrative_process_step_departments
    ENABLE TRIGGER administrative_process_step_departments_immutable;

ALTER TABLE administrative_process_step_departments
    ALTER COLUMN department_name SET NOT NULL,
    ADD CONSTRAINT administrative_process_step_departments_name_nonblank
        CHECK (btrim(department_name) <> '');

CREATE TABLE administrative_workflow_publication_keys (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    idempotency_key UUID NOT NULL,
    subject_key VARCHAR(80) NOT NULL CHECK (subject_key ~ '^[a-z0-9][a-z0-9._-]{0,79}$'),
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    workflow_id UUID,
    workflow_version INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (organization_id, idempotency_key),
    CHECK ((workflow_id IS NULL) = (workflow_version IS NULL)),
    FOREIGN KEY (organization_id, subject_key, workflow_id, workflow_version)
        REFERENCES administrative_workflows (organization_id, subject_key, id, version)
);

CREATE FUNCTION guard_administrative_workflow_publication_key() RETURNS trigger AS $$
BEGIN
    IF TG_OP IN ('DELETE', 'TRUNCATE') THEN
        RAISE EXCEPTION 'workflow publication idempotency keys are immutable';
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF OLD.workflow_id IS NOT NULL
           OR NEW.workflow_id IS NULL
           OR NEW.organization_id IS DISTINCT FROM OLD.organization_id
           OR NEW.idempotency_key IS DISTINCT FROM OLD.idempotency_key
           OR NEW.subject_key IS DISTINCT FROM OLD.subject_key
           OR NEW.request_hash IS DISTINCT FROM OLD.request_hash
           OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
            RAISE EXCEPTION 'workflow publication idempotency keys are immutable';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER administrative_workflow_publication_keys_guard
    BEFORE UPDATE OR DELETE ON administrative_workflow_publication_keys
    FOR EACH ROW EXECUTE FUNCTION guard_administrative_workflow_publication_key();
CREATE TRIGGER administrative_workflow_publication_keys_no_truncate
    BEFORE TRUNCATE ON administrative_workflow_publication_keys
    FOR EACH STATEMENT EXECUTE FUNCTION guard_administrative_workflow_publication_key();

CREATE FUNCTION set_administrative_process_event_time() RETURNS trigger AS $$
DECLARE
    previous_event_time TIMESTAMPTZ;
BEGIN
    PERFORM 1
      FROM administrative_processes
     WHERE organization_id = NEW.organization_id AND id = NEW.process_id
     FOR UPDATE;

    SELECT MAX(occurred_at)
      INTO previous_event_time
      FROM administrative_process_events
     WHERE organization_id = NEW.organization_id AND process_id = NEW.process_id;

    IF previous_event_time IS NULL THEN
        NEW.occurred_at = clock_timestamp();
    ELSE
        NEW.occurred_at = GREATEST(clock_timestamp(), previous_event_time + INTERVAL '1 microsecond');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE administrative_process_events
    ALTER COLUMN occurred_at SET DEFAULT clock_timestamp();

CREATE TRIGGER administrative_process_events_monotonic_time
    BEFORE INSERT ON administrative_process_events
    FOR EACH ROW EXECUTE FUNCTION set_administrative_process_event_time();
