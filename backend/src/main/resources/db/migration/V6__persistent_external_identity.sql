CREATE TABLE user_accounts (
    username VARCHAR(120) PRIMARY KEY CHECK (btrim(username) <> ''),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    registration_date DATE,
    registration_date_source VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (registration_date_source IN ('IMPORTED', 'UNKNOWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((registration_date IS NULL) = (registration_date_source = 'UNKNOWN'))
);

-- Preserve a known imported registration date only when every known organization profile agrees.
-- Missing or conflicting historical dates remain explicitly unknown.
WITH usernames AS (
    SELECT username FROM staff_memberships
    UNION
    SELECT username FROM organization_memberships
    UNION
    SELECT username FROM staff_profiles
), known_dates AS (
    SELECT username, min(registration_date) AS registration_date,
           count(DISTINCT registration_date) AS distinct_dates
      FROM staff_profiles
     WHERE registration_date IS NOT NULL
     GROUP BY username
)
INSERT INTO user_accounts (username, registration_date, registration_date_source)
SELECT usernames.username,
       CASE WHEN known_dates.distinct_dates = 1 THEN known_dates.registration_date END,
       CASE WHEN known_dates.distinct_dates = 1 THEN 'IMPORTED' ELSE 'UNKNOWN' END
  FROM usernames
  LEFT JOIN known_dates USING (username)
ON CONFLICT (username) DO NOTHING;

CREATE TABLE external_identity_links (
    issuer TEXT NOT NULL CHECK (btrim(issuer) <> ''),
    subject TEXT NOT NULL CHECK (btrim(subject) <> ''),
    username VARCHAR(120) NOT NULL REFERENCES user_accounts(username),
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    linked_by VARCHAR(120) NOT NULL CHECK (btrim(linked_by) <> ''),
    PRIMARY KEY (issuer, subject)
);
CREATE INDEX external_identity_links_username_idx ON external_identity_links(username);
