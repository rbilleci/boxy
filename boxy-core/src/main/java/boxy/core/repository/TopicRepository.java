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

    public long create(String path, String name, int partitions) {
        return queryOne("{CALL sp_topics__create(?,?,?)}",
                ID_MAPPER, path, name, partitions).orElseThrow();
    }

    public Optional<Topic> find(String path, String name) {
        return queryOne("""
                        SELECT t.*
                          FROM topics t
                          JOIN namespaces_with_path_view n ON n.id = t.namespace_id
                         WHERE n.path = ?
                           AND t.name = ?
                        """,
                TOPIC_MAPPER, path, name);
    }

    public void delete(String path, String name) {
        update("{CALL sp_topics__delete(?,?)}", path, name);
    }

}
