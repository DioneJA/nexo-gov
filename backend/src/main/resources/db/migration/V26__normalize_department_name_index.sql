DO $$
DECLARE conflicting_names TEXT;
BEGIN
    SELECT string_agg(format('%s: %s', organization_id, names), E'\n')
      INTO conflicting_names
      FROM (
          SELECT organization_id,
                 string_agg(format('%s (%s)', name, ids), '; ' ORDER BY lower(btrim(name))) AS names
            FROM (
                SELECT organization_id, lower(btrim(name)) AS normalized_name,
                       string_agg(name, ', ' ORDER BY name) AS name,
                       string_agg(id::text, ', ' ORDER BY id) AS ids
                  FROM departments
                 GROUP BY organization_id, lower(btrim(name))
                HAVING count(*) > 1
            ) duplicate_groups
           GROUP BY organization_id
      ) duplicates;

    IF conflicting_names IS NOT NULL THEN
        RAISE EXCEPTION 'Cannot enforce trimmed, case-insensitive department name uniqueness: existing duplicates require manual reconciliation.'
            USING DETAIL = conflicting_names,
                  HINT = 'Reconcile duplicate department names, then rerun the migration. No records were changed.';
    END IF;
END;
$$;

DROP INDEX departments_organization_name_case_insensitive_idx;

CREATE UNIQUE INDEX departments_organization_name_case_insensitive_idx
    ON departments (organization_id, lower(btrim(name)));
