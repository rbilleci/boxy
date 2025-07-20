package boxy.persistence.dao;

import boxy.persistence.model.Topic;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.transaction.Transaction;

import java.util.Optional;

@RegisterConstructorMapper(Topic.class)
public interface TopicDao extends SqlObject {

    @Transaction
    default long create(String tenant, String name, int partitions) {
        final var h = getHandle();
        // Add the topic
        final var topicId = h.createUpdate("INSERT INTO topics (tenant, name, partitions) VALUES (:tenant, :name, :partitions)")
                .bind("tenant", tenant)
                .bind("name", name)
                .bind("partitions", partitions)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one();
        // Add partitions
        h.createUpdate("""
                        INSERT INTO partitions (topic_id, partition_number) WITH RECURSIVE numbers (n) AS (
                            SELECT 0
                            UNION ALL
                            SELECT n + 1 FROM numbers WHERE n < :partitions - 1)
                        SELECT :topicId, n FROM numbers;
                        """)
                .bind("partitions", partitions)
                .bind("topicId", topicId)
                .execute();
        return topicId;
    }

    @SqlQuery("SELECT * FROM topics WHERE id = :id")
    Optional<Topic> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM topics WHERE tenant = :tenant AND name = :name")
    Optional<Topic> find(@Bind("tenant") String tenant, @Bind("name") String name);

    @SqlUpdate("DELETE FROM topics WHERE tenant = :tenant AND name = :name")
    void delete(@Bind("tenant") String tenant, @Bind("name") String name);

    @SqlUpdate("DELETE FROM topics WHERE id = :topicId")
    void delete(@Bind("topicId") long topicId);
}
