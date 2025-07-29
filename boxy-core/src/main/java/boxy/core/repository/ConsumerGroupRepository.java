package boxy.core.repository;

import boxy.core.mapper.ConsumerGroupMapper;
import boxy.core.domain.ConsumerGroup;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class ConsumerGroupRepository extends BaseRepository {

    private static final ConsumerGroupMapper CONSUMER_GROUP_MAPPER = new ConsumerGroupMapper();

    public ConsumerGroupRepository(DataSource ds) {
        super(ds);
    }

    public long create(String tenant, String name) {
        return queryOne("{CALL sp_consumer_groups_create(?, ?)}", rs -> rs.getLong(1), tenant, name)
                .orElseThrow();
    }

    public void delete(String tenant, String name) {
        update("{CALL sp_consumer_groups_delete(?, ?)}", tenant, name);
    }

    public Optional<ConsumerGroup> find(String tenant, String name) {
        return queryOne("SELECT * FROM consumer_groups WHERE tenant = ? AND name = ?",
                CONSUMER_GROUP_MAPPER,
                tenant, name);
    }

    public List<ConsumerGroup> findAll(int limit, int offset) {
        return query("SELECT * FROM consumer_groups ORDER BY id LIMIT ? OFFSET ?",
                CONSUMER_GROUP_MAPPER,
                limit, offset);
    }
}
