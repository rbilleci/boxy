CREATE EVENT event_sequencer
    ON SCHEDULE EVERY 1 MINUTE
    ON COMPLETION PRESERVE
    ENABLE
    DO CALL sp_events__sequence_loop(100);
