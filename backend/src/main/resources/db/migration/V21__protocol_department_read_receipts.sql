CREATE TABLE protocol_read_receipts (
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    department_id UUID NOT NULL,
    username VARCHAR(120) NOT NULL,
    read_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    PRIMARY KEY (organization_id, protocol_id, department_id, username),
    FOREIGN KEY (organization_id, protocol_id) REFERENCES protocols(organization_id, id),
    FOREIGN KEY (organization_id, department_id) REFERENCES departments(organization_id, id)
);

CREATE TRIGGER protocol_read_receipts_immutable
    BEFORE UPDATE OR DELETE ON protocol_read_receipts
    FOR EACH ROW EXECUTE FUNCTION prevent_protocol_event_mutation();

CREATE TRIGGER protocol_read_receipts_no_truncate
    BEFORE TRUNCATE ON protocol_read_receipts
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_protocol_event_mutation();
