package boxy.core.repository;

import boxy.core.mapper.TopicMapper;
import boxy.core.model.Topic;

import javax.sql.DataSource;
import java.util.Optional;

public final class TopicRepository extends BaseRepository {

    private static final TopicMapper TOPIC_MAPPER = new TopicMapper();

    public TopicRepository(DataSource ds) {
        super(ds);
    }

    public long create(String tenant, String name, int partitions) {
        return queryOne("{CALL sp_topics_create(?,?,?)}", rs -> rs.getLong(1), tenant, name, partitions).orElseThrow();
    }

    public Optional<Topic> find(String tenant, String name) {
        return queryOne("SELECT * FROM topics WHERE tenant = ? AND name = ?", TOPIC_MAPPER, tenant, name);
    }

    public void delete(String tenant, String name) {
        update("{CALL sp_topics_delete(?,?)}", tenant, name);
    }

}
