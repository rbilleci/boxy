package boxy.persistence.dao;

import org.jdbi.v3.sqlobject.SqlObject;

import java.util.List;

public interface EventDao extends SqlObject {

    default void publish(long partitionId, String data) {
        final var json = "[" + data + "]";
        getHandle().createUpdate("CALL sp_publish_events(:pid,:events)")
                .bind("pid", partitionId)
                .bind("events", json)
                .execute();
    }

    default void publish(long partitionId, final List<String> data) {
        final var json = "[" + String.join(",", data) + "]";
        getHandle().createUpdate("CALL sp_publish_events(:pid,:events)")
                .bind("pid", partitionId)
                .bind("events", json)
                .execute();
    }

}
