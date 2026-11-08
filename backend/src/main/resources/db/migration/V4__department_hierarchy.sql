ALTER TABLE departments
    ADD COLUMN parent_department_id UUID;

ALTER TABLE departments
    ADD CONSTRAINT departments_parent_same_organization_fk
        FOREIGN KEY (organization_id, parent_department_id)
        REFERENCES departments(organization_id, id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT departments_cannot_parent_itself_check
        CHECK (parent_department_id IS NULL OR parent_department_id <> id);

CREATE INDEX departments_active_children_idx
    ON departments (organization_id, parent_department_id, name, id)
    WHERE active = true;
