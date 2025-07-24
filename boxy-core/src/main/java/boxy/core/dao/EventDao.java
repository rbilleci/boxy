package boxy.core.dao;

import boxy.core.jdbc.BaseDao;

import javax.sql.DataSource;
import java.sql.SQLException;

public class EventDao extends BaseDao {

    public EventDao(DataSource ds) {
        super(ds);
    }

    public void publish(String tenant, String topic, String key, String data) throws SQLException {
        update("CALL sp_events_publish(?,?,?,?)", tenant, topic, key, data);
    }

    public void publishAdvanced(long partitionId, String data) throws SQLException {
        update("CALL sp_events_publish_advanced(?,?)", partitionId, data);
    }
}
