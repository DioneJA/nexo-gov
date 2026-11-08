ALTER TABLE administrative_workflow_step_departments
    ADD COLUMN can_decide BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE administrative_process_step_departments
    ADD COLUMN can_decide BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE administrative_processes
    ADD COLUMN decision_outcome VARCHAR(16),
    ADD COLUMN decision_actor VARCHAR(120),
    ADD COLUMN decision_at TIMESTAMPTZ,
    ADD CONSTRAINT administrative_processes_decision_state_check
        CHECK (
            (decision_outcome IS NULL AND decision_actor IS NULL AND decision_at IS NULL)
            OR (
                decision_outcome IS NOT NULL
                AND decision_outcome IN ('DEFERIDO', 'INDEFERIDO')
                AND decision_actor IS NOT NULL
                AND btrim(decision_actor) <> ''
                AND decision_at IS NOT NULL
            )
        );

ALTER TABLE administrative_process_events
    ADD COLUMN decision_outcome VARCHAR(16);

ALTER TABLE administrative_process_events
    DROP CONSTRAINT administrative_process_events_action_check,
    DROP CONSTRAINT administrative_process_events_check,
    ADD CONSTRAINT administrative_process_events_action_check
        CHECK (action IN ('OPENED', 'ADVANCED', 'DECIDED')),
    ADD CONSTRAINT administrative_process_events_shape_check
        CHECK (
            (
                action = 'OPENED'
                AND from_step_order IS NULL
                AND from_department_id IS NULL
                AND decision_outcome IS NULL
            )
            OR (
                action = 'ADVANCED'
                AND from_step_order IS NOT NULL
                AND from_department_id IS NOT NULL
                AND decision_outcome IS NULL
            )
            OR (
                action = 'DECIDED'
                AND from_step_order IS NOT NULL
                AND from_step_order = to_step_order
                AND from_department_id IS NOT NULL
                AND from_department_id = to_department_id
                AND decision_outcome IS NOT NULL
                AND decision_outcome IN ('DEFERIDO', 'INDEFERIDO')
            )
        );
