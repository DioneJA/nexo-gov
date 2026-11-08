CREATE TABLE administrative_process_deadlines (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    process_id UUID NOT NULL,
    title VARCHAR(180) NOT NULL CHECK (btrim(title) <> ''),
    due_at TIMESTAMPTZ NOT NULL,
    created_by VARCHAR(120) NOT NULL CHECK (btrim(created_by) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    FOREIGN KEY (organization_id, process_id)
        REFERENCES administrative_processes (organization_id, id)
);

CREATE INDEX administrative_process_deadlines_process_due_idx
    ON administrative_process_deadlines (organization_id, process_id, due_at, created_at, id);

CREATE FUNCTION prevent_administrative_process_deadline_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'administrative process deadlines are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER administrative_process_deadlines_immutable
    BEFORE UPDATE OR DELETE ON administrative_process_deadlines
    FOR EACH ROW EXECUTE FUNCTION prevent_administrative_process_deadline_mutation();
CREATE TRIGGER administrative_process_deadlines_no_truncate
    BEFORE TRUNCATE ON administrative_process_deadlines
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_administrative_process_deadline_mutation();
