package boxy.core.mapper;

import boxy.core.domain.ConsumerRegistration;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ConsumerRegistrationMapper implements RowMapper<ConsumerRegistration> {
    @Override
    public ConsumerRegistration map(final ResultSet rs) throws SQLException {
        return new ConsumerRegistration(
                rs.getString("consumer_id"),
                rs.getLong("subscription_topic_id"),
                rs.getLong("topic_id"));
    }
}
