CREATE TABLE organization_branding (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id),
    display_name_override VARCHAR(160),
    accent_token VARCHAR(32) NOT NULL DEFAULT 'ACTION_BLUE',
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT organization_branding_display_name_override_not_blank
        CHECK (display_name_override IS NULL OR (btrim(display_name_override) <> '' AND display_name_override = btrim(display_name_override))),
    CONSTRAINT organization_branding_accent_token_allowed
        CHECK (accent_token IN ('NEXO_NAVY', 'NEXO_BLUE', 'ACTION_BLUE', 'SUCCESS_GREEN'))
);
