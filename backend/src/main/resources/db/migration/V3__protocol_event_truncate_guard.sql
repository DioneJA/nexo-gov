CREATE TRIGGER protocol_events_no_truncate
    BEFORE TRUNCATE ON protocol_events
    FOR EACH STATEMENT EXECUTE FUNCTION prevent_protocol_event_mutation();
