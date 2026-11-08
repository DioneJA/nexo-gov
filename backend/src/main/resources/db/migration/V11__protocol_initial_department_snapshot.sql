ALTER TABLE protocols
    ADD COLUMN initial_assigned_department_name VARCHAR(180);

-- Existing protocols may already have been forwarded. Their initial destination
-- cannot be reconstructed reliably, so this column intentionally remains NULL
-- for every pre-V11 row. New protocols store the verified opening destination.
