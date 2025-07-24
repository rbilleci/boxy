package boxy.core.dao;

import boxy.core.jdbc.BaseDao;
import boxy.core.jdbc.RowMapper;
import boxy.core.model.Topic;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;

public class TopicDao extends BaseDao {
    private static final RowMapper<Topic> MAPPER = rs -> new Topic(
            rs.getLong("id"),
            rs.getString("tenant"),
            rs.getString("name"),
            rs.getInt("partitions")
    );

    public TopicDao(DataSource ds) {
        super(ds);
    }

    public long create(String tenant, String name, int partitions) throws SQLException {
        return queryOne("CALL sp_topics_create(?,?,?)", rs -> rs.getLong(1), tenant, name, partitions).orElseThrow();
    }

    public Optional<Topic> find(long id) throws SQLException {
        return queryOne("SELECT * FROM topics WHERE id = ?", MAPPER, id);
    }

    public Optional<Topic> find(String tenant, String name) throws SQLException {
        return queryOne("SELECT * FROM topics WHERE tenant = ? AND name = ?", MAPPER, tenant, name);
    }

    public void delete(long topicId) throws SQLException {
        update("CALL sp_topics_delete(?)", topicId);
    }
}
