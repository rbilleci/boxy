package boxy.core.dao;

import boxy.core.model.Worker;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class WorkerDao extends BaseDao {

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

    public boolean heartbeat(String nodeId, long consumerGroupId) {
        return queryOne("CALL sp_workers_heartbeat(?,?)", rs -> rs.getInt(1), nodeId, consumerGroupId)
                .orElse(0) > 0;
    }

    public Optional<Worker> find(long id) {
        return queryOne("SELECT * FROM workers WHERE id = ?", MAPPER, id);
    }

    public Optional<Worker> find(String nodeId, long consumerGroupId) {
        return queryOne("SELECT * FROM workers WHERE node_id = ? AND consumer_group_id = ?", MAPPER, nodeId, consumerGroupId);
    }
    
    public List<Worker> findActiveByConsumerGroup(long consumerGroupId, int heartbeatTimeoutSeconds) {
        return query(
            "SELECT * FROM workers WHERE consumer_group_id = ? AND last_heartbeat >= DATE_SUB(CURRENT_TIMESTAMP(3), INTERVAL ? SECOND)",
            MAPPER, consumerGroupId, heartbeatTimeoutSeconds);
    }
    
    public int cleanupExpiredWorkers(int heartbeatTimeoutSeconds) {
        return queryOne("CALL sp_workers_cleanup_expired(?)", rs -> rs.getInt(1), heartbeatTimeoutSeconds)
                .orElse(0);
    }
}
