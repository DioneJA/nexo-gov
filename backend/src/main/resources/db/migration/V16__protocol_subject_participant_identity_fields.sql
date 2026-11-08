CREATE TABLE protocol_subject_participant_fields (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id UUID NOT NULL,
    subject_id UUID NOT NULL,
    field_key VARCHAR(64) NOT NULL CHECK (field_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    label VARCHAR(120) NOT NULL CHECK (btrim(label) <> ''),
    input_type VARCHAR(16) NOT NULL CHECK (input_type IN ('TEXT', 'EMAIL', 'PHONE', 'DOCUMENT')),
    required BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order BETWEEN 0 AND 1000),
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, id),
    FOREIGN KEY (organization_id, subject_id)
        REFERENCES protocol_subjects(organization_id, id)
);

CREATE UNIQUE INDEX protocol_subject_participant_fields_key_idx
    ON protocol_subject_participant_fields (organization_id, subject_id, field_key);

CREATE INDEX protocol_subject_participant_fields_order_idx
    ON protocol_subject_participant_fields (organization_id, subject_id, display_order, id);
