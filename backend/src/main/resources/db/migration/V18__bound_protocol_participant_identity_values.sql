ALTER TABLE protocol_participant_answers
    ADD CONSTRAINT protocol_participant_answers_value_length_check
        CHECK (char_length(field_value) BETWEEN 1 AND 500 AND btrim(field_value) <> '');
