package boxy.core.repository;

import boxy.core.domain.LeasePolicy;
import boxy.core.mapper.LeasePolicyMapper;

import javax.sql.DataSource;

public final class LeasePolicyRepository extends BaseRepository {

    private static final LeasePolicyMapper MAPPER = new LeasePolicyMapper();

    public LeasePolicyRepository(DataSource ds) {
        super(ds);
    }

    public LeasePolicy get() {
        return queryOne("SELECT * FROM lease_policies WHERE id = 1", MAPPER).orElseThrow();
    }

    public void update(LeasePolicy policy) {
        update("{CALL sp_lease_policies__update(?,?)}",
                policy.activeConsumersLimit(),
                policy.leaseReleasePeriod());
    }
}
