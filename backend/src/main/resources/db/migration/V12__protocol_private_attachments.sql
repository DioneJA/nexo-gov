CREATE TABLE protocol_subject_attachment_requirements (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    name VARCHAR(120) NOT NULL CHECK (btrim(name) <> ''),
    required BOOLEAN NOT NULL,
    allowed_extensions TEXT[] NOT NULL CHECK (cardinality(allowed_extensions) > 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    UNIQUE (organization_id, subject_id, id),
    FOREIGN KEY (organization_id, subject_id)
        REFERENCES protocol_subjects(organization_id, id)
);

CREATE UNIQUE INDEX protocol_subject_attachment_requirement_name_ci_idx
    ON protocol_subject_attachment_requirements (organization_id, subject_id, lower(btrim(name)))
    WHERE active = TRUE;

CREATE TABLE protocol_attachments (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    requirement_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    sha256 CHAR(64) NOT NULL,
    object_key VARCHAR(512) NOT NULL UNIQUE,
    download_count BIGINT NOT NULL DEFAULT 0 CHECK (download_count >= 0),
    uploaded_by VARCHAR(120) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, protocol_id, id),
    FOREIGN KEY (organization_id, protocol_id)
        REFERENCES protocols(organization_id, id),
    FOREIGN KEY (organization_id, subject_id, requirement_id)
        REFERENCES protocol_subject_attachment_requirements(organization_id, subject_id, id)
);

CREATE INDEX protocol_attachments_protocol_idx
    ON protocol_attachments (organization_id, protocol_id, uploaded_at, id);
