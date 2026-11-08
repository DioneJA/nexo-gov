CREATE TABLE protocol_subjects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    name VARCHAR(180) NOT NULL CHECK (btrim(name) <> ''),
    online_available BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id)
);

CREATE UNIQUE INDEX protocol_subjects_organization_name_ci_idx
    ON protocol_subjects (organization_id, lower(btrim(name)));

ALTER TABLE protocols
    ADD COLUMN protocol_subject_id UUID;

ALTER TABLE protocols
    ADD CONSTRAINT protocols_subject_same_organization_fk
    FOREIGN KEY (organization_id, protocol_subject_id)
    REFERENCES protocol_subjects (organization_id, id);

CREATE INDEX protocols_organization_subject_idx
    ON protocols (organization_id, protocol_subject_id)
    WHERE protocol_subject_id IS NOT NULL;
