package boxy.core.dao;

import boxy.core.model.ConsumerGroup;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public class ConsumerGroupDao extends BaseDao {

    private static final RowMapper<ConsumerGroup> MAPPER = rs -> new ConsumerGroup(
            rs.getLong("id"),
            rs.getString("tenant"),
            rs.getString("name"));

    public ConsumerGroupDao(DataSource ds) {
        super(ds);
    }

    public long create(String tenant, String name) {
        return queryOne("CALL sp_consumer_groups_create(?, ?)", rs -> rs.getLong(1), tenant, name)
                .orElseThrow();
    }

    public void delete(long id) {
        update("CALL sp_consumer_groups_delete(?)", id);
    }

    public Optional<ConsumerGroup> find(long id) {
        return queryOne("SELECT * FROM consumer_groups WHERE id = ?", MAPPER, id);
    }

    public Optional<ConsumerGroup> find(String tenant, String name) {
        return queryOne("SELECT * FROM consumer_groups WHERE tenant = ? AND name = ?", MAPPER, tenant, name);
    }

    public List<ConsumerGroup> findAll(int limit, int offset) {
        return query("SELECT * FROM consumer_groups ORDER BY id LIMIT ? OFFSET ?", MAPPER, limit, offset);
    }
}
