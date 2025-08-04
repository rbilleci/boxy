package boxy.core.mapper;

import boxy.core.domain.LeasePolicy;

import java.sql.ResultSet;
import java.sql.SQLException;

public class LeasePolicyMapper implements RowMapper<LeasePolicy> {
    @Override
    public LeasePolicy map(ResultSet rs) throws SQLException {
        return new LeasePolicy(
                rs.getInt("id"),
                rs.getInt("active_consumers_limit"),
                rs.getInt("lease_release_period"));
    }
}
