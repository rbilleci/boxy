package boxy.core.dao;

import boxy.core.model.Topic;

import javax.sql.DataSource;
import java.util.Optional;

public final class TopicDao extends BaseDao {

    private static final RowMapper<Topic> MAPPER = rs -> new Topic(
            rs.getLong("id"),
            rs.getString("tenant"),
            rs.getString("name"),
            rs.getInt("partitions"));

    public TopicDao(DataSource ds) {
        super(ds);
    }

    public long create(String tenant, String name, int partitions) {
        return queryOne("CALL sp_topics_create(?,?,?)", rs -> rs.getLong(1), tenant, name, partitions).orElseThrow();
    }

    public Optional<Topic> find(String tenant, String name) {
        return queryOne("SELECT * FROM topics WHERE tenant = ? AND name = ?", MAPPER, tenant, name);
    }

    public void delete(String tenant, String name) {
        update("CALL sp_topics_delete(?,?)", tenant, name);
    }

}
