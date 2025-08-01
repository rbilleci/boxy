package boxy.core.repository;

import boxy.core.mapper.IdMapper;
import boxy.core.mapper.RowMapper;
import boxy.core.mapper.TopicMapper;
import boxy.core.domain.Topic;

import javax.sql.DataSource;
import java.util.Optional;

public final class TopicRepository extends BaseRepository {

    private static final TopicMapper TOPIC_MAPPER = new TopicMapper();
    private static final RowMapper<Long> ID_MAPPER = new IdMapper();

    public TopicRepository(DataSource ds) {
        super(ds);
    }

    public long create(String tenant, String namespace, String name, int partitions) {
        return queryOne("{CALL sp_topics__create(?,?,?,?)}",
                ID_MAPPER, tenant, namespace, name, partitions).orElseThrow();
    }

    public Optional<Topic> find(String tenant, String namespace, String name) {
        return queryOne("""
                        SELECT t.*
                          FROM topics t
                          JOIN namespaces n ON t.namespace_id = n.id
                         WHERE n.tenant = ?
                           AND n.name = ?
                           AND t.name = ?
                        """,
                TOPIC_MAPPER, tenant, namespace, name);
    }

    public void delete(String tenant, String namespace, String name) {
        update("{CALL sp_topics__delete(?,?,?)}", tenant, namespace, name);
    }

}
