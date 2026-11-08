-- Use wall-clock time at row insertion. PostgreSQL now() is fixed at transaction
-- start, which can make a transaction that waited on the protocol row lock
-- appear earlier than the interaction or event that released the lock.
ALTER TABLE protocol_events
    ALTER COLUMN created_at SET DEFAULT clock_timestamp();

ALTER TABLE protocol_interactions
    ALTER COLUMN created_at SET DEFAULT clock_timestamp();
