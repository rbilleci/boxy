package boxy.core.it;

import boxy.core.repository.EventRepository;
import boxy.core.repository.PartitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class EventPollIT extends BaseIT {

    private EventRepository eventRepository;
    private PartitionRepository partitionRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        eventRepository = new EventRepository(dataSource);
        partitionRepository = new PartitionRepository(dataSource);
        data = TestData.seed(dataSource);
    }


    @Test
    void poll_benchmarkOverhead() throws SQLException {
        final long subscriptionId = data.subscriptions().getFirst().id();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TestData.TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-0";

        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");

        awaitSequencer(partitionId);

        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?,?,?)}")) {
            poll.setLong(1, subscriptionId);
            poll.setString(2, consumerId);
            poll.setInt(3, 10);

            final var start = System.currentTimeMillis();
            for (int i = 0; i < 10_000; i++) {
                try (var rs = poll.executeQuery()) {
                    while (rs.next()) {
                        rs.getLong("event_id");
                    }
                }
            }
            final var end = System.currentTimeMillis();
            System.out.printf("polling took %s ms\n", (end - start));
        }
    }


    @Test
    void poll_returnsEventsAndLocksCursor() throws SQLException {
        final long subscriptionId = data.subscriptions().getFirst().id();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TestData.TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-1";

        eventRepository.publish(partitionId, "{\"v\":1}");
        eventRepository.publish(partitionId, "{\"v\":2}");

        awaitSequencer(partitionId);

        final List<Long> polled = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?,?,?)}")) {
            poll.setLong(1, subscriptionId);
            poll.setString(2, consumerId);
            poll.setInt(3, 10);
            try (var rs = poll.executeQuery()) {
                while (rs.next()) {
                    polled.add(rs.getLong("event_id"));
                }
            }
        }

        assertThat(polled).hasSize(2);

        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT locked_by FROM cursors WHERE subscription_id = ? AND partition_id = ?")) {
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
        final long subscriptionId = data.subscriptions().getFirst().id();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TestData.TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-2";

        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");

        awaitSequencer(partitionId);

        final List<Long> polled = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?,?,?)}")) {
            poll.setLong(1, subscriptionId);
            poll.setString(2, consumerId);
            poll.setInt(3, 100);
            try (var rs = poll.executeQuery()) {
                while (rs.next()) {
                    polled.add(rs.getLong("event_id"));
                }
            }
        }

        assertThat(polled).hasSize(3);
    }

    @Test
    void poll_returnsOversizedSequence() throws SQLException {
        final long subscriptionId = data.subscriptions().getFirst().id();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TestData.TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-3";

        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");
        eventRepository.publish(partitionId, "{}");

        awaitSequencer(partitionId);

        final List<Long> polled = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?,?,?)}")) {
            poll.setLong(1, subscriptionId);
            poll.setString(2, consumerId);
            poll.setInt(3, 2);
            try (var rs = poll.executeQuery()) {
                while (rs.next()) {
                    polled.add(rs.getLong("event_id"));
                }
            }
        }

        assertThat(polled).hasSize(3);
    }

    @Test
    void poll_returnsOnlyFirstOversizedSequence() throws SQLException {
        final long subscriptionId = data.subscriptions().getFirst().id();
        final long partitionId =
                partitionRepository.find(TestData.PATH_A, TestData.TOPIC_A, 0).orElseThrow().id();
        final String consumerId = "consumer-4";

        for (int i = 0; i < 20; i++) {
            eventRepository.publish(partitionId, "{}");
        }

        awaitSequencer(partitionId);

        final List<Long> polled = new ArrayList<>();
        try (var conn = dataSource.getConnection();
             var poll = conn.prepareCall("{CALL sp_events__poll(?,?,?)}")) {
            poll.setLong(1, subscriptionId);
            poll.setString(2, consumerId);
            poll.setInt(3, 3);
            try (var rs = poll.executeQuery()) {
                while (rs.next()) {
                    polled.add(rs.getLong("event_id"));
                }
            }
        }

        assertThat(polled).hasSize(10);
    }

    private void awaitSequencer(long partitionId) throws SQLException {
        final long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            if (hasSequencedEvents(partitionId)) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for sequencer", e);
            }
        }
        throw new AssertionError("Sequencer did not process events for partition " + partitionId);
    }

    private boolean hasSequencedEvents(long partitionId) throws SQLException {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM sequences WHERE partition_id = ?")) {
            ps.setLong(1, partitionId);
            try (var rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }
}
