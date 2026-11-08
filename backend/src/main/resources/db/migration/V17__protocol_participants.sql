CREATE TABLE protocol_participants (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    name VARCHAR(180) NOT NULL CHECK (btrim(name) <> ''),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (organization_id, protocol_id, id),
    FOREIGN KEY (organization_id, protocol_id)
        REFERENCES protocols(organization_id, id)
);

CREATE INDEX protocol_participants_tenant_protocol_created_idx
    ON protocol_participants(organization_id, protocol_id, created_at, id);

CREATE TABLE protocol_participant_field_snapshots (
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    field_key VARCHAR(64) NOT NULL CHECK (field_key ~ '^[a-z][a-z0-9_]{0,63}$'),
    label VARCHAR(120) NOT NULL CHECK (btrim(label) <> ''),
    input_type VARCHAR(16) NOT NULL CHECK (input_type IN ('TEXT', 'EMAIL', 'PHONE', 'DOCUMENT')),
    required BOOLEAN NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order BETWEEN 0 AND 1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, protocol_id, field_key),
    UNIQUE (organization_id, protocol_id, field_key, label, input_type),
    FOREIGN KEY (organization_id, protocol_id)
        REFERENCES protocols(organization_id, id)
);

CREATE TABLE protocol_participant_answers (
    organization_id UUID NOT NULL,
    protocol_id UUID NOT NULL,
    participant_id UUID NOT NULL,
    field_key VARCHAR(64) NOT NULL,
    label VARCHAR(120) NOT NULL,
    input_type VARCHAR(16) NOT NULL,
    field_value TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, protocol_id, participant_id, field_key),
    FOREIGN KEY (organization_id, protocol_id, participant_id)
        REFERENCES protocol_participants(organization_id, protocol_id, id),
    FOREIGN KEY (organization_id, protocol_id, field_key, label, input_type)
        REFERENCES protocol_participant_field_snapshots
            (organization_id, protocol_id, field_key, label, input_type)
);

ALTER TABLE protocol_interactions
    ADD COLUMN participant_id UUID;

-- PostgreSQL generated these names from the inline checks in V13.
ALTER TABLE protocol_interactions
    DROP CONSTRAINT protocol_interactions_actor_type_check,
    DROP CONSTRAINT protocol_interactions_check;

ALTER TABLE protocol_interactions
    ADD CONSTRAINT protocol_interactions_participant_fk
        FOREIGN KEY (organization_id, protocol_id, participant_id)
        REFERENCES protocol_participants(organization_id, protocol_id, id),
    ADD CONSTRAINT protocol_interactions_actor_scope_check
        CHECK (
            (actor_type = 'APPLICANT'
                AND participant_id IS NULL
                AND department_id IS NULL
                AND visibility = 'PUBLIC')
            OR (actor_type = 'STAFF'
                AND participant_id IS NULL
                AND department_id IS NOT NULL)
            OR (actor_type = 'PARTICIPANT'
                AND participant_id IS NOT NULL
                AND department_id IS NULL
                AND visibility = 'PUBLIC')
        );
