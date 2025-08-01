package boxy.core.repository;

import javax.sql.DataSource;

public final class EventRepository extends BaseRepository {

    public EventRepository(DataSource ds) {
        super(ds);
    }

    public void publish(String tenant, String namespace, String topic, String key, String data) {
        update("{CALL sp_events__publish(?,?,?,?,?)}", tenant, namespace, topic, key, data);
    }

    public void publishAdvanced(long partitionId, String data) {
        update("{CALL sp_events__publish_advanced(?,?)}", partitionId, data);
    }
}
