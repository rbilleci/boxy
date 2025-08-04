package boxy.core.repository;

import boxy.core.domain.MetricsPolicy;
import boxy.core.mapper.MetricsPolicyMapper;

import javax.sql.DataSource;

public final class MetricsPolicyRepository extends BaseRepository {

    private static final MetricsPolicyMapper MAPPER = new MetricsPolicyMapper();

    public MetricsPolicyRepository(DataSource ds) {
        super(ds);
    }

    public MetricsPolicy get() {
        return queryOne("SELECT * FROM metrics_policies WHERE id = 1", MAPPER).orElseThrow();
    }

    public void update(MetricsPolicy policy) {
        update("{CALL sp_metrics_policies__update(?)}",
                policy.metricsRefreshInterval());
    }
}
