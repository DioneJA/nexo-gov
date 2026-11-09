CREATE TABLE organization_privacy_notice (
    organization_id UUID PRIMARY KEY REFERENCES organizations(id),
    explanatory_text VARCHAR(2000) NOT NULL,
    privacy_policy_url VARCHAR(2048) NOT NULL,
    created_by VARCHAR(120) NOT NULL,
    updated_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT organization_privacy_notice_text_not_blank
        CHECK (btrim(explanatory_text) <> '' AND explanatory_text = btrim(explanatory_text)),
    CONSTRAINT organization_privacy_notice_url_https_only
        CHECK (privacy_policy_url ~ '^https://[^[:space:]@]+$')
);
