package boxy.persistence.dao;

import org.jdbi.v3.sqlobject.SqlObject;

import java.util.List;

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

    default void publishMulti(long partitionId, final List<String> data) {
        final var json = "[" + String.join(",", data) + "]";
        getHandle().createUpdate("CALL sp_events_publish_multi(:pid,:events)")
                .bind("pid", partitionId)
                .bind("events", json)
                .execute();
    }

}
