package boxy.core.mapper;

import boxy.core.domain.Subscription;

import java.sql.ResultSet;
import java.sql.SQLException;

public class SubscriptionMapper implements RowMapper<Subscription> {
    @Override
    public Subscription map(final ResultSet rs) throws SQLException {
        return new Subscription(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getTimestamp("last_modified_at").toInstant());
    }
}
