package boxy.core.repository;

import boxy.core.domain.HeartbeatPolicy;
import boxy.core.mapper.HeartbeatPolicyMapper;

import javax.sql.DataSource;

public final class HeartbeatPolicyRepository extends BaseRepository {

    private static final HeartbeatPolicyMapper MAPPER = new HeartbeatPolicyMapper();

    public HeartbeatPolicyRepository(DataSource ds) {
        super(ds);
    }

    public HeartbeatPolicy get() {
        return queryOne("SELECT * FROM heartbeat_policies WHERE id = 1", MAPPER).orElseThrow();
    }

    public void update(HeartbeatPolicy policy) {
        update("{CALL sp_heartbeat_policies__update(?,?,?,?)}",
                policy.heartbeatDeadlineMultiplier(),
                policy.heartbeatIntervalBaseline(),
                policy.heartbeatIntervalLimit(),
                policy.heartbeatTargetQps());
    }
}
