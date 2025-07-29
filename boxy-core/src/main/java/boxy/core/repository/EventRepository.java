package boxy.core.repository;

import javax.sql.DataSource;

public final class EventRepository extends BaseRepository {

    public EventRepository(DataSource ds) {
        super(ds);
    }

    public void publish(String tenant, String topic, String key, String data) {
        update("CALL sp_events_publish(?,?,?,?)", tenant, topic, key, data);
    }

    public void publishAdvanced(long partitionId, String data) {
        update("CALL sp_events_publish_advanced(?,?)", partitionId, data);
    }
}
