ALTER TABLE protocol_subjects
    ADD COLUMN destination_department_id UUID;

ALTER TABLE protocol_subjects
    ADD CONSTRAINT protocol_subject_destination_same_organization_fk
    FOREIGN KEY (organization_id, destination_department_id)
    REFERENCES departments (organization_id, id);

CREATE INDEX protocol_subjects_destination_department_idx
    ON protocol_subjects (organization_id, destination_department_id)
    WHERE destination_department_id IS NOT NULL;
