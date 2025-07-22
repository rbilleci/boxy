package boxy.persistence.dao;

import org.jdbi.v3.sqlobject.SqlObject;

import java.util.List;

public interface EventDao extends SqlObject {

    default void publish(long partitionId, String data) {
        final var h = getHandle();
        h.createUpdate("INSERT INTO events (partition_id, data) VALUES (?, ?)")
                .bind(0, partitionId)
                .bind(1, data)
                .execute();
    }

    default void publish(long partitionId, List<String> data) {
        final var h = getHandle();
        final var b = h.prepareBatch("INSERT INTO events (partition_id, data) VALUES (?, ?)");
        data.forEach(d -> b.bind(0, partitionId).bind(1, d));
        b.execute();
    }
}
