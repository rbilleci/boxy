package boxy.core.dao;

import org.jdbi.v3.sqlobject.SqlObject;

public interface EventDao extends SqlObject {

    default void publish(String tenant, String topic, String key, String data) {
        getHandle().createUpdate("CALL sp_events_publish(:tenant,:topic,:key,:data)")
                .bind("tenant", tenant)
                .bind("topic", topic)
                .bind("key", key)
                .bind("data", data)
                .execute();
    }

    default void publishAdvanced(long partitionId, String data) {
        getHandle().createUpdate("CALL sp_events_publish_advanced(:pid,:event)")
                .bind("pid", partitionId)
                .bind("event", data)
                .execute();
    }

}
