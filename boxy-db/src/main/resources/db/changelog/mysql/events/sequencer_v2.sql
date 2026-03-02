-- sequencer event v2 — calls sp_sequence_loop(0) so batch size is read from boxy_config
--
-- Item #92: sp_sequence_loop v4 reads sequencer.batch.size from boxy_config when
-- called with 0.  The default is 1000 if the key is not set.  To change it:
--   UPDATE boxy_config SET config_value = '2000' WHERE config_key = 'sequencer.batch.size';
DROP EVENT IF EXISTS sequencer;
CREATE EVENT sequencer
    ON SCHEDULE EVERY 1 MINUTE
    ON COMPLETION PRESERVE
    ENABLE
    DO CALL sp_sequence_loop(0);
