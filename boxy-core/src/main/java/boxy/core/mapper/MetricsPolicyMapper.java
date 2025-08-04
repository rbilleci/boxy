package boxy.core.mapper;

import boxy.core.domain.MetricsPolicy;

import java.sql.ResultSet;
import java.sql.SQLException;

public class MetricsPolicyMapper implements RowMapper<MetricsPolicy> {
    @Override
    public MetricsPolicy map(ResultSet rs) throws SQLException {
        return new MetricsPolicy(
                rs.getInt("id"),
                rs.getInt("metrics_refresh_interval"));
    }
}
