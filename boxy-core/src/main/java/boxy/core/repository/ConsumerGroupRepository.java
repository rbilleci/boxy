package boxy.core.repository;

import boxy.core.mapper.IdMapper;
import boxy.core.mapper.RowMapper;
import boxy.core.mapper.ConsumerGroupMapper;
import boxy.core.domain.ConsumerGroup;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class ConsumerGroupRepository extends BaseRepository {

    private static final ConsumerGroupMapper CONSUMER_GROUP_MAPPER = new ConsumerGroupMapper();
    private static final RowMapper<Long> ID_MAPPER = new IdMapper();

    public ConsumerGroupRepository(DataSource ds) {
        super(ds);
    }

    public long create(String name) {
        return queryOne("{CALL sp_consumer_groups__create(?)}", ID_MAPPER, name).orElseThrow();
    }

    public void delete(String name) {
        update("{CALL sp_consumer_groups__delete(?)}", name);
    }

    public long subscribe(String consumerGroup, String path, String topic) {
        return queryOne("{CALL sp_consumer_groups__subscribe(?,?,?)}", ID_MAPPER, consumerGroup, path, topic).orElseThrow();
    }

    public void unsubscribe(String consumerGroup, String path, String topic) {
        update("{CALL sp_consumer_groups__unsubscribe(?,?,?)}", consumerGroup, path, topic);
    }

    public Optional<ConsumerGroup> find(String name) {
        return queryOne("SELECT * FROM consumer_groups WHERE name = ?", CONSUMER_GROUP_MAPPER, name);
    }

    public List<ConsumerGroup> findAll(int limit, int offset) {
        return query("SELECT * FROM consumer_groups ORDER BY id LIMIT ? OFFSET ?", CONSUMER_GROUP_MAPPER, limit, offset);
    }
}
