package boxy.core.dao;

import boxy.core.model.Worker;

import javax.sql.DataSource;
import java.util.Optional;

public class WorkerDao extends BaseDao {

    private static final RowMapper<Worker> MAPPER = rs -> new Worker(
            rs.getLong("id"),
            rs.getString("node_id"),
            rs.getLong("consumer_group_id"),
            rs.getInt("weight"),
            rs.getTimestamp("last_heartbeat").toInstant());

    public WorkerDao(DataSource ds) {
        super(ds);
    }

    public long register(String nodeId, long consumerGroupId, int weight) {
        return queryOne("CALL sp_workers_register(?,?,?)", rs -> rs.getLong(1), nodeId, consumerGroupId, weight)
                .orElseThrow();
    }

    public void deregister(long id) {
        update("CALL sp_workers_deregister(?)", id);
    }

    public Optional<Worker> find(long id) {
        return queryOne("SELECT * FROM workers WHERE id = ?", MAPPER, id);
    }

    public Optional<Worker> find(String nodeId, long consumerGroupId) {
        return queryOne("SELECT * FROM workers WHERE node_id = ? AND consumer_group_id = ?", MAPPER, nodeId, consumerGroupId);
    }
}
