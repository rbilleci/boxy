package boxy.core.it;

import boxy.mysql.repository.ConsumerRepository;
import boxy.mysql.repository.CursorRepository;
import boxy.mysql.repository.EventRepository;
import boxy.mysql.repository.PartitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EventPollIT extends BaseIT {

    private EventRepository eventRepository;
    private PartitionRepository partitionRepository;
    private ConsumerRepository consumerRepository;
    private CursorRepository cursorRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        eventRepository = new EventRepository(dataSource);
        partitionRepository = new PartitionRepository(dataSource);
        consumerRepository = new ConsumerRepository(dataSource);
        cursorRepository = new CursorRepository(dataSource);
        data = TestData.seed(dataSource);
    }


    @Test
    void poll_benchmarkOverhead() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-0";

        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");
        awaitSequencer();

        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?)}")) {
            poll.setString(1, consumerId);

            final var start = System.currentTimeMillis();
            for (int i = 0; i < 10_000; i++) {
                boolean hasResults = poll.execute();
                if (hasResults) {
                    try (var rs = poll.getResultSet()) {
                        while (rs.next()) {
                            rs.getLong("event_id");
                        }
                    }
                }
                while (poll.getMoreResults()) {
                    try (var ignored = poll.getResultSet()) {
                        // consume metadata
                    }
                }
            }
            final var end = System.currentTimeMillis();
            System.out.printf("polling took %s ms\n", (end - start));
        }
    }


    @Test
    void poll_returnsEventsAndLocksCursor() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final long subscriptionId = subscription.id();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-1";

        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        eventRepository.publish(partitionId, "{\"v\":1}");
        eventRepository.publish(partitionId, "{\"v\":2}");
        awaitSequencer();

        final List<Long> polled = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?)}")) {
            poll.setString(1, consumerId);
            boolean hasResults = poll.execute();
            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    while (rs.next()) {
                        polled.add(rs.getLong("event_id"));
                    }
                }
            }
            while (poll.getMoreResults()) {
                try (var ignored = poll.getResultSet()) {
                    // consume metadata
                }
            }
        }

        assertThat(polled).hasSize(2);

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT l.consumer_id " +
                             "FROM consumer_leases l " +
                             "JOIN cursors c ON c.id = l.cursor_id " +
                             "WHERE c.subscription_id = ? AND c.partition_id = ?")) {
            ps.setLong(1, subscriptionId);
            ps.setLong(2, partitionId);
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo(consumerId);
            }
        }
    }

    @Test
    void poll_respectsBatchSize() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-2";

        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");
        awaitSequencer();

        final List<Long> polled = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?)}")) {
            poll.setString(1, consumerId);
            boolean hasResults = poll.execute();
            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    while (rs.next()) {
                        polled.add(rs.getLong("event_id"));
                    }
                }
            }
            while (poll.getMoreResults()) {
                try (var ignored = poll.getResultSet()) {
                    // consume metadata
                }
            }
        }

        assertThat(polled).hasSize(3);
    }

    @Test
    void poll_returnsMetadataAfterCommitAndBackoff() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-3";

        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        eventRepository.publish(partitionId, "{\"v\":3}");
        awaitSequencer();

        long cursorId = -1L;
        long sequence = -1L;
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?)}")) {
            poll.setString(1, consumerId);
            boolean hasResults = poll.execute();
            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    assertThat(rs.next()).isTrue();
                    cursorId = rs.getLong("cursor_id");
                    sequence = rs.getLong("sequence");
                }
            }
            while (poll.getMoreResults()) {
                try (var ignored = poll.getResultSet()) {
                    // consume metadata from first poll
                }
            }
        }

        cursorRepository.commit(consumerId, Map.of(cursorId, sequence));

        double pollingProbability = -1;
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?)}")) {
            poll.setString(1, consumerId);
            boolean hasResults = poll.execute();
            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    assertThat(rs.next()).isFalse();
                }
            }
            if (poll.getMoreResults()) {
                try (var rs = poll.getResultSet()) {
                    assertThat(rs.next()).isTrue();
                    pollingProbability = rs.getDouble("polling_probability");
                }
            }
        }

        assertThat(pollingProbability).isGreaterThan(0);
    }

    @Test
    void poll_resumesFromLastReadPositionForSession() throws SQLException {
        final var subscription = data.subscriptions().getFirst();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-4";

        consumerRepository.register(consumerId, subscription.name(), List.of(PATH_A + "/" + TOPIC_A));

        eventRepository.publish(partitionId, "{\"v\":4}");
        eventRepository.publish(partitionId, "{\"v\":5}");
        awaitSequencer();

        final List<Long> firstPoll = pollEvents(consumerId);
        final List<Long> secondPoll = pollEvents(consumerId);

        assertThat(firstPoll).hasSize(2);
        assertThat(secondPoll).isEmpty();
    }

    private void awaitSequencer() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Long> pollEvents(final String consumerId) throws SQLException {
        final List<Long> polled = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?)}")) {
            poll.setString(1, consumerId);
            boolean hasResults = poll.execute();
            if (hasResults) {
                try (var rs = poll.getResultSet()) {
                    while (rs.next()) {
                        polled.add(rs.getLong("event_id"));
                    }
                }
            }
            while (poll.getMoreResults()) {
                try (var ignored = poll.getResultSet()) {
                    // consume metadata
                }
            }
        }
        return polled;
    }

}
