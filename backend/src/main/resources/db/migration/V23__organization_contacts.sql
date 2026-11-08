CREATE TABLE organization_contacts (
    id UUID NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    contact_type VARCHAR(32) NOT NULL
        CHECK (contact_type IN ('PERSON', 'MUNICIPAL_DEPARTMENT', 'LEGAL_ENTITY')),
    name VARCHAR(180) NOT NULL CHECK (btrim(name) <> ''),
    email VARCHAR(254),
    phone VARCHAR(40),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by VARCHAR(120) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (organization_id, id),
    CHECK (email IS NULL OR btrim(email) <> ''),
    CHECK (phone IS NULL OR btrim(phone) <> '')
);

CREATE INDEX organization_contacts_active_name_idx
    ON organization_contacts (organization_id, lower(name), id)
    WHERE active = true;

CREATE INDEX organization_contacts_type_idx
    ON organization_contacts (organization_id, contact_type, lower(name), id);
