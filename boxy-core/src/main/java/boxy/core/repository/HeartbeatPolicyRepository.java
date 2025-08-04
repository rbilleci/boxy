package boxy.core.repository;

import boxy.core.domain.HeartbeatPolicy;
import boxy.core.mapper.HeartbeatPolicyMapper;

import javax.sql.DataSource;

public final class HeartbeatPolicyRepository extends BaseRepository {

    private static final HeartbeatPolicyMapper MAPPER = new HeartbeatPolicyMapper();

    public HeartbeatPolicyRepository(final DataSource ds) {
        super(ds);
    }

    public HeartbeatPolicy get() {
        return queryOne("SELECT * FROM heartbeat_policies LIMIT 1", MAPPER).orElseThrow();
    }

    public void update(final HeartbeatPolicy policy) {
        update("{CALL sp_heartbeat_policies__update(?,?,?,?)}",
                policy.heartbeatDeadlineMultiplier(),
                policy.heartbeatIntervalBaseline(),
                policy.heartbeatIntervalLimit(),
                policy.heartbeatTargetQps());
    }
}
