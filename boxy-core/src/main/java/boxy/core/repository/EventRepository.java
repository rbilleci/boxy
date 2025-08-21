package boxy.core.repository;

import javax.sql.DataSource;

public final class EventRepository extends BaseRepository {

    public EventRepository(final DataSource ds) {
        super(ds);
    }

    public void publish(final String path, final String topic, final String key, final String data) {
        update("{CALL sp_events__publish(?,?,?,?)}", path, topic, key, data);
    }

    public void publish(final long partitionId, final String data) {
        update("{CALL sp_events__publish_advanced(?,?)}", partitionId, data);
    }

}
