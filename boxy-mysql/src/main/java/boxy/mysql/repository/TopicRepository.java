package boxy.mysql.repository;

import boxy.core.mapper.IdMapper;
import boxy.core.mapper.RowMapper;
import boxy.core.mapper.TopicMapper;
import boxy.core.domain.Topic;
import boxy.core.repository.BaseRepository;

import javax.sql.DataSource;
import java.util.Optional;

public final class TopicRepository extends BaseRepository {

    private static final TopicMapper TOPIC_MAPPER = new TopicMapper();
    private static final RowMapper<Long> ID_MAPPER = new IdMapper();

    public TopicRepository(final DataSource ds) {
        super(ds);
    }

    public long create(final String path, final String name, final int partitions) {
        return queryOne("{CALL sp_topics__create(?,?,?)}", ID_MAPPER, path, name, partitions).orElseThrow();
    }

    public Optional<Topic> find(final String path, final String name) {
        return queryOne("""
                SELECT *
                  FROM topics
                 WHERE namespace_id = fn_resolve_namespace_id(?)
                   AND name = ?
                """, TOPIC_MAPPER, path, name);
    }

    public void delete(final String path, final String name) {
        update("{CALL sp_topics__delete(?,?)}", path, name);
    }

}
