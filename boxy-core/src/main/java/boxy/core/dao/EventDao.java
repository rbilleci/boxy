package boxy.core.dao;

import javax.sql.DataSource;

public final class EventDao extends BaseDao {

    public EventDao(DataSource ds) {
        super(ds);
    }

    public void publish(String tenant, String topic, String key, String data) {
        update("CALL sp_events_publish(?,?,?,?)", tenant, topic, key, data);
    }

    public void publishAdvanced(long partitionId, String data) {
        update("CALL sp_events_publish_advanced(?,?)", partitionId, data);
    }
}
