package boxy.core.repository;

import boxy.core.mapper.TopicMapper;
import boxy.core.domain.Topic;

import javax.sql.DataSource;
import java.util.Optional;

public final class TopicRepository extends BaseRepository {

    private static final TopicMapper TOPIC_MAPPER = new TopicMapper();

    public TopicRepository(DataSource ds) {
        super(ds);
    }

    public long create(long namespaceId, String name, int partitions) {
        return queryOne("{CALL sp_topics__create(?,?,?)}", rs -> rs.getLong(1), namespaceId, name, partitions).orElseThrow();
    }

    public Optional<Topic> find(long namespaceId, String name) {
        return queryOne("SELECT * FROM topics WHERE namespace_id = ? AND name = ?", TOPIC_MAPPER, namespaceId, name);
    }

    public void delete(long namespaceId, String name) {
        update("{CALL sp_topics__delete(?,?)}", namespaceId, name);
    }

}
