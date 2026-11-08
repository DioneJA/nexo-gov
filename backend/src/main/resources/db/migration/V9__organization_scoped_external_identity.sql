DO $$
DECLARE
    legacy_link_count BIGINT;
BEGIN
    SELECT count(*) INTO legacy_link_count FROM external_identity_links;
    IF legacy_link_count > 0 THEN
        RAISE EXCEPTION 'Cannot apply V9: % globally scoped external identity link(s) require organization reconciliation.', legacy_link_count
            USING HINT = 'Reconcile each issuer + subject + username to an organization using an authoritative source. V9 does not convert or remove legacy links; no data was changed.';
    END IF;
END;
$$;

ALTER TABLE external_identity_links
    ADD COLUMN organization_id UUID NOT NULL REFERENCES organizations(id);

ALTER TABLE external_identity_links
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE external_identity_links
    DROP CONSTRAINT external_identity_links_pkey;

ALTER TABLE external_identity_links
    ADD CONSTRAINT external_identity_links_pkey PRIMARY KEY (organization_id, issuer, subject);

CREATE INDEX external_identity_links_organization_username_idx
    ON external_identity_links (organization_id, username);
