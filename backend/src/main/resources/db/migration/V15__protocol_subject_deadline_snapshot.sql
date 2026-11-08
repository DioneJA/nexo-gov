ALTER TABLE protocol_subjects
    ADD COLUMN automatic_deadline_days INTEGER
        CHECK (automatic_deadline_days IS NULL OR automatic_deadline_days > 0);

ALTER TABLE protocols
    ADD COLUMN automatic_deadline_days INTEGER
        CHECK (automatic_deadline_days IS NULL OR automatic_deadline_days > 0);
