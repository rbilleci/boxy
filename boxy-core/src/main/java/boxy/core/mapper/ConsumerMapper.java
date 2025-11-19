package boxy.core.mapper;

import boxy.core.domain.Consumer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

public class ConsumerMapper implements RowMapper<Consumer> {
    @Override
    public Consumer map(final ResultSet rs) throws SQLException {
        return new Consumer(
                rs.getString("id"),
                rs.getLong("subscription_id"),
                rs.getDouble("weight"),
                rs.getTimestamp("heartbeat_detected_at").toInstant(),
                rs.getDouble("heartbeat_interval"),
                rs.getTimestamp("heartbeat_deadline").toInstant(),
                parseTopicIds(rs.getString("topic_ids")));
    }

    private List<Long> parseTopicIds(final String topicIdsJson) {
        if (topicIdsJson == null || topicIdsJson.isBlank()) {
            return List.of();
        }

        final String trimmed = topicIdsJson.trim();
        if (trimmed.length() < 2 || trimmed.charAt(0) != '[' || trimmed.charAt(trimmed.length() - 1) != ']') {
            return List.of();
        }

        final String body = trimmed.substring(1, trimmed.length() - 1).trim();
        if (body.isEmpty()) {
            return List.of();
        }

        return Arrays.stream(body.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
