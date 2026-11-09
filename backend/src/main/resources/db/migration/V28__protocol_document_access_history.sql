CREATE TABLE protocol_document_access_events (
    id BIGSERIAL PRIMARY KEY,
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    document_type VARCHAR(24) NOT NULL CHECK (document_type IN ('PROTOCOL', 'ATTACHMENT', 'INTERACTION')),
    document_id UUID,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('STAFF', 'APPLICANT', 'PARTICIPANT', 'ANONYMOUS')),
    actor_reference VARCHAR(120),
    access_type VARCHAR(16) NOT NULL CHECK (access_type IN ('READ', 'ATTEMPT')),
    outcome VARCHAR(12) NOT NULL CHECK (outcome IN ('ALLOWED', 'DENIED')),
    accessed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, protocol_id) REFERENCES protocols(organization_id, id),
    CHECK ((document_type = 'PROTOCOL' AND document_id = protocol_id)
        OR (document_type IN ('ATTACHMENT', 'INTERACTION') AND document_id IS NOT NULL)),
    CHECK ((actor_type = 'ANONYMOUS' AND actor_reference IS NULL)
        OR (actor_type <> 'ANONYMOUS' AND actor_reference IS NOT NULL))
);

CREATE INDEX protocol_document_access_tenant_protocol_time_idx
    ON protocol_document_access_events(organization_id, protocol_id, accessed_at, id);

CREATE INDEX protocol_document_access_document_time_idx
    ON protocol_document_access_events(organization_id, protocol_id, document_type, document_id, accessed_at, id);

CREATE TRIGGER protocol_document_access_events_immutable
    BEFORE UPDATE OR DELETE ON protocol_document_access_events
    FOR EACH ROW EXECUTE FUNCTION prevent_protocol_event_mutation();

CREATE TRIGGER protocol_document_access_events_no_truncate
    BEFORE TRUNCATE ON protocol_document_access_events
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_protocol_event_mutation();
