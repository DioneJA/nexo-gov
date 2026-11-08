CREATE TABLE protocol_interactions (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    actor VARCHAR(120) NOT NULL,
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('APPLICANT', 'STAFF')),
    department_id UUID,
    visibility VARCHAR(16) NOT NULL CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    message VARCHAR(8000) NOT NULL CHECK (btrim(message) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, protocol_id, id),
    FOREIGN KEY (organization_id, protocol_id) REFERENCES protocols(organization_id, id),
    FOREIGN KEY (organization_id, department_id) REFERENCES departments(organization_id, id),
    CHECK ((actor_type = 'APPLICANT' AND department_id IS NULL AND visibility = 'PUBLIC')
        OR (actor_type = 'STAFF' AND department_id IS NOT NULL))
);

CREATE INDEX protocol_interactions_tenant_protocol_time_idx
    ON protocol_interactions(organization_id, protocol_id, created_at, id);

CREATE TRIGGER protocol_interactions_immutable
    BEFORE UPDATE OR DELETE ON protocol_interactions
    FOR EACH ROW EXECUTE FUNCTION prevent_protocol_event_mutation();
CREATE TRIGGER protocol_interactions_no_truncate
    BEFORE TRUNCATE ON protocol_interactions
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_protocol_event_mutation();

ALTER TABLE protocol_attachments
    ADD COLUMN interaction_id UUID,
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC'
        CHECK (visibility IN ('PUBLIC', 'INTERNAL')),
    ADD CONSTRAINT protocol_attachments_interaction_fk
        FOREIGN KEY (organization_id, protocol_id, interaction_id)
        REFERENCES protocol_interactions(organization_id, protocol_id, id);

CREATE INDEX protocol_attachments_interaction_idx
    ON protocol_attachments(organization_id, protocol_id, interaction_id, uploaded_at, id);

CREATE TABLE protocol_attachment_downloads (
    id BIGSERIAL PRIMARY KEY,
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    attachment_id UUID NOT NULL,
    actor VARCHAR(120) NOT NULL,
    audience VARCHAR(16) NOT NULL CHECK (audience IN ('PUBLIC', 'STAFF')),
    downloaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (organization_id, protocol_id, attachment_id)
        REFERENCES protocol_attachments(organization_id, protocol_id, id)
);

CREATE INDEX protocol_attachment_downloads_tenant_protocol_time_idx
    ON protocol_attachment_downloads(organization_id, protocol_id, downloaded_at, id);

CREATE TRIGGER protocol_attachment_downloads_immutable
    BEFORE UPDATE OR DELETE ON protocol_attachment_downloads
    FOR EACH ROW EXECUTE FUNCTION prevent_protocol_event_mutation();
CREATE TRIGGER protocol_attachment_downloads_no_truncate
    BEFORE TRUNCATE ON protocol_attachment_downloads
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_protocol_event_mutation();
