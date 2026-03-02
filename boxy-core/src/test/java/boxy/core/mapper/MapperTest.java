package boxy.core.mapper;

import boxy.core.domain.Consumer;
import boxy.core.domain.Cursor;
import boxy.core.domain.Topic;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Item #120 (item 83): Unit tests for mapper classes.
 * Uses a simple fake ResultSet implementation via Proxy to avoid test dependencies
 * on Mockito or other mocking frameworks.
 */
class MapperTest {

    /**
     * Create a fake ResultSet using Java reflection Proxy.
     * Supports getLong, getInt, getString, getDouble, getTimestamp for the given column names.
     */
    private ResultSet fakeRs(final Map<String, Object> values) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> {
                    final String methodName = method.getName();
                    if (args == null || args.length == 0) {
                        return null;
                    }

                    final String columnName = (String) args[0];
                    final Object value = values.get(columnName);

                    return switch (methodName) {
                        case "getLong" -> {
                            if (value instanceof Number n) {
                                yield n.longValue();
                            }
                            yield 0L;
                        }
                        case "getInt" -> {
                            if (value instanceof Number n) {
                                yield n.intValue();
                            }
                            yield 0;
                        }
                        case "getString" -> value != null ? value.toString() : null;
                        case "getDouble" -> {
                            if (value instanceof Number n) {
                                yield n.doubleValue();
                            }
                            yield 0.0;
                        }
                        case "getTimestamp" -> {
                            if (value instanceof Timestamp t) {
                                yield t;
                            } else if (value instanceof Instant i) {
                                yield Timestamp.from(i);
                            } else if (value instanceof Long l) {
                                yield new Timestamp(l);
                            }
                            yield new Timestamp(0);
                        }
                        default -> throw new UnsupportedOperationException(methodName);
                    };
                });
    }

    // =========================================================================
    // TopicMapper Tests
    // =========================================================================

    @Test
    void topicMapper_mapsAllFields() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", 123L);
        values.put("namespace_id", 456L);
        values.put("name", "test-topic");
        values.put("partitions", 16);
        values.put("created_at", Timestamp.from(now));
        values.put("last_modified_at", Timestamp.from(now.plusSeconds(60)));

        final var rs = fakeRs(values);
        final var mapper = new TopicMapper();
        final var topic = mapper.map(rs);

        assertThat(topic.id()).isEqualTo(123L);
        assertThat(topic.namespaceId()).isEqualTo(456L);
        assertThat(topic.name()).isEqualTo("test-topic");
        assertThat(topic.partitions()).isEqualTo(16);
        assertThat(topic.createdAt()).isEqualTo(now);
        assertThat(topic.lastModifiedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void topicMapper_zeroPartitions() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", 1L);
        values.put("namespace_id", 1L);
        values.put("name", "empty-topic");
        values.put("partitions", 0);
        values.put("created_at", Timestamp.from(now));
        values.put("last_modified_at", Timestamp.from(now));

        final var rs = fakeRs(values);
        final var mapper = new TopicMapper();
        final var topic = mapper.map(rs);

        assertThat(topic.partitions()).isEqualTo(0);
    }

    // =========================================================================
    // CursorMapper Tests
    // =========================================================================

    @Test
    void cursorMapper_mapsAllFields() throws SQLException {
        final Map<String, Object> values = new HashMap<>();
        values.put("id", 100L);
        values.put("subscription_id", 200L);
        values.put("subscription_topic_id", 300L);
        values.put("topic_id", 400L);
        values.put("partition_id", 500L);
        values.put("random_key", 42);
        values.put("position", 999L);

        final var rs = fakeRs(values);
        final var mapper = new CursorMapper();
        final var cursor = mapper.map(rs);

        assertThat(cursor.id()).isEqualTo(100L);
        assertThat(cursor.subscriptionId()).isEqualTo(200L);
        assertThat(cursor.subscriptionTopicId()).isEqualTo(300L);
        assertThat(cursor.topicId()).isEqualTo(400L);
        assertThat(cursor.partitionId()).isEqualTo(500L);
        assertThat(cursor.randomKey()).isEqualTo(42);
        assertThat(cursor.position()).isEqualTo(999L);
    }

    @Test
    void cursorMapper_zeroPosition() throws SQLException {
        final Map<String, Object> values = new HashMap<>();
        values.put("id", 1L);
        values.put("subscription_id", 1L);
        values.put("subscription_topic_id", 1L);
        values.put("topic_id", 1L);
        values.put("partition_id", 1L);
        values.put("random_key", 0);
        values.put("position", 0L);

        final var rs = fakeRs(values);
        final var mapper = new CursorMapper();
        final var cursor = mapper.map(rs);

        assertThat(cursor.position()).isEqualTo(0L);
    }

    // =========================================================================
    // ConsumerMapper Tests
    // =========================================================================

    @Test
    void consumerMapper_mapsAllFields() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", "consumer-1");
        values.put("subscription_id", 100L);
        values.put("heartbeat_detected_at", Timestamp.from(now));
        values.put("heartbeat_interval", 30.0);
        values.put("heartbeat_deadline", Timestamp.from(now.plusSeconds(30)));
        values.put("topic_ids", "[1,2,3]");

        final var rs = fakeRs(values);
        final var mapper = new ConsumerMapper();
        final var consumer = mapper.map(rs);

        assertThat(consumer.id()).isEqualTo("consumer-1");
        assertThat(consumer.subscriptionId()).isEqualTo(100L);
        assertThat(consumer.heartbeatDetectedAt()).isEqualTo(now);
        assertThat(consumer.heartbeatInterval()).isEqualTo(30.0);
        assertThat(consumer.heartbeatDeadline()).isEqualTo(now.plusSeconds(30));
        assertThat(consumer.topicIds()).containsExactly(1L, 2L, 3L);
    }

    @Test
    void consumerMapper_parseTopicIds_emptyArray() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", "consumer-2");
        values.put("subscription_id", 200L);
        values.put("heartbeat_detected_at", Timestamp.from(now));
        values.put("heartbeat_interval", 60.0);
        values.put("heartbeat_deadline", Timestamp.from(now.plusSeconds(60)));
        values.put("topic_ids", "[]");

        final var rs = fakeRs(values);
        final var mapper = new ConsumerMapper();
        final var consumer = mapper.map(rs);

        assertThat(consumer.topicIds()).isEmpty();
    }

    @Test
    void consumerMapper_parseTopicIds_nullValue() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", "consumer-3");
        values.put("subscription_id", 300L);
        values.put("heartbeat_detected_at", Timestamp.from(now));
        values.put("heartbeat_interval", 45.0);
        values.put("heartbeat_deadline", Timestamp.from(now.plusSeconds(45)));
        values.put("topic_ids", null);

        final var rs = fakeRs(values);
        final var mapper = new ConsumerMapper();
        final var consumer = mapper.map(rs);

        assertThat(consumer.topicIds()).isEmpty();
    }

    @Test
    void consumerMapper_parseTopicIds_blankValue() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", "consumer-4");
        values.put("subscription_id", 400L);
        values.put("heartbeat_detected_at", Timestamp.from(now));
        values.put("heartbeat_interval", 50.0);
        values.put("heartbeat_deadline", Timestamp.from(now.plusSeconds(50)));
        values.put("topic_ids", "   ");

        final var rs = fakeRs(values);
        final var mapper = new ConsumerMapper();
        final var consumer = mapper.map(rs);

        assertThat(consumer.topicIds()).isEmpty();
    }

    @Test
    void consumerMapper_parseTopicIds_singleElement() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", "consumer-5");
        values.put("subscription_id", 500L);
        values.put("heartbeat_detected_at", Timestamp.from(now));
        values.put("heartbeat_interval", 25.0);
        values.put("heartbeat_deadline", Timestamp.from(now.plusSeconds(25)));
        values.put("topic_ids", "[42]");

        final var rs = fakeRs(values);
        final var mapper = new ConsumerMapper();
        final var consumer = mapper.map(rs);

        assertThat(consumer.topicIds()).containsExactly(42L);
    }

    @Test
    void consumerMapper_parseTopicIds_largeArray() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", "consumer-6");
        values.put("subscription_id", 600L);
        values.put("heartbeat_detected_at", Timestamp.from(now));
        values.put("heartbeat_interval", 35.0);
        values.put("heartbeat_deadline", Timestamp.from(now.plusSeconds(35)));
        values.put("topic_ids", "[1,2,3,4,5,6,7,8,9,10]");

        final var rs = fakeRs(values);
        final var mapper = new ConsumerMapper();
        final var consumer = mapper.map(rs);

        assertThat(consumer.topicIds()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);
    }

    @Test
    void consumerMapper_parseTopicIds_withWhitespace() throws SQLException {
        final var now = Instant.now();
        final Map<String, Object> values = new HashMap<>();
        values.put("id", "consumer-7");
        values.put("subscription_id", 700L);
        values.put("heartbeat_detected_at", Timestamp.from(now));
        values.put("heartbeat_interval", 40.0);
        values.put("heartbeat_deadline", Timestamp.from(now.plusSeconds(40)));
        values.put("topic_ids", "[ 10 , 20 , 30 ]");

        final var rs = fakeRs(values);
        final var mapper = new ConsumerMapper();
        final var consumer = mapper.map(rs);

        assertThat(consumer.topicIds()).containsExactly(10L, 20L, 30L);
    }

    // =========================================================================
    // IdMapper Tests (edge cases)
    // =========================================================================

    @Test
    void idMapper_mapsId() throws SQLException {
        final Map<String, Object> values = new HashMap<>();
        values.put("id", 999L);

        final var rs = fakeRs(values);
        final var mapper = new IdMapper();
        final var id = mapper.map(rs);

        assertThat(id).isEqualTo(999L);
    }

    @Test
    void idMapper_zeroId() throws SQLException {
        final Map<String, Object> values = new HashMap<>();
        values.put("id", 0L);

        final var rs = fakeRs(values);
        final var mapper = new IdMapper();
        final var id = mapper.map(rs);

        assertThat(id).isEqualTo(0L);
    }

}
