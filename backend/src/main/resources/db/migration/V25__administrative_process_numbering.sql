ALTER TABLE administrative_workflows
    ADD COLUMN numbering_mode VARCHAR(24) NOT NULL DEFAULT 'BY_SUBJECT'
        CHECK (numbering_mode IN ('BY_SUBJECT', 'GENERAL_SEQUENCE')),
    ADD COLUMN number_pattern VARCHAR(120) NOT NULL DEFAULT '{assunto}-{numero}'
        CHECK (btrim(number_pattern) <> '');

ALTER TABLE administrative_processes
    ADD COLUMN process_number VARCHAR(240),
    ADD CONSTRAINT administrative_processes_number_not_blank
        CHECK (process_number IS NULL OR btrim(process_number) <> ''),
    ADD CONSTRAINT administrative_processes_number_per_organization_unique
        UNIQUE (organization_id, process_number);

CREATE TABLE administrative_process_number_counters (
    organization_id UUID NOT NULL REFERENCES organizations(id),
    counter_scope VARCHAR(100) NOT NULL,
    next_value BIGINT NOT NULL CHECK (next_value > 0),
    PRIMARY KEY (organization_id, counter_scope)
);

CREATE TABLE administrative_process_number_registry_locks (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id)
);

CREATE FUNCTION prevent_administrative_process_number_mutation() RETURNS trigger AS $$
BEGIN
    IF NEW.process_number IS DISTINCT FROM OLD.process_number THEN
        RAISE EXCEPTION 'administrative process numbers are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER administrative_process_number_immutable
    BEFORE UPDATE OF process_number ON administrative_processes
    FOR EACH ROW EXECUTE FUNCTION prevent_administrative_process_number_mutation();
