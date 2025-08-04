package boxy.core.it;

import boxy.core.repository.ConsumerGroupRepository;
import boxy.core.domain.ConsumerGroup;
import boxy.core.repository.ConsumerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ConsumerGroupIT extends BaseIT {

    private ConsumerGroupRepository consumerGroupRepository;
    private ConsumerRepository consumerRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        consumerGroupRepository = new ConsumerGroupRepository(dataSource);
        consumerRepository = new ConsumerRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void create_whenCreated_isPresent() {
        System.out.println("[DEBUG_LOG] Running create_whenCreated_isPresent test");
        final var consumerGroup = consumerGroupRepository.find(CONSUMER_GROUP_A).orElseThrow();
        System.out.println("[DEBUG_LOG] Found consumer group: " + consumerGroup);
        System.out.println("[DEBUG_LOG] Consumer Group fields:");
        System.out.println("[DEBUG_LOG]   ID: " + consumerGroup.id());
        System.out.println("[DEBUG_LOG]   Name: " + consumerGroup.name());
        System.out.println("[DEBUG_LOG]   Heartbeat Interval Default: " + consumerGroup.heartbeatIntervalBaseline());
        System.out.println("[DEBUG_LOG]   Heartbeat Deadline Multiplier: " + consumerGroup.heartbeatDeadlineMultiplier());
        System.out.println("[DEBUG_LOG]   Lease Release Period: " + consumerGroup.leaseReleasePeriod());
        System.out.println("[DEBUG_LOG]   Heartbeat QPS Target: " + consumerGroup.heartbeatTargetQPS());
        System.out.println("[DEBUG_LOG]   Heartbeat Interval Limit: " + consumerGroup.heartbeatIntervalLimit());
        System.out.println("[DEBUG_LOG]   Active Consumers Count: " + consumerGroup.activeConsumers());
        System.out.println("[DEBUG_LOG]   Total Weight: " + consumerGroup.activeConsumersWeight());
        System.out.println("[DEBUG_LOG]   Active Partitions Count: " + consumerGroup.activePartitions());
        System.out.println("[DEBUG_LOG]   Last Modified At: " + consumerGroup.lastModifiedAt());
        assertThat(consumerGroup).extracting(ConsumerGroup::name).isEqualTo(CONSUMER_GROUP_A);
    }

    @Test
    void deleted_whenDeleted_isNotPresent() {
        consumerGroupRepository.find(CONSUMER_GROUP_A).orElseThrow();
        consumerRepository.findAll(100, 0).forEach(c -> consumerRepository.delete(c.id()));
        consumerGroupRepository.delete(CONSUMER_GROUP_A);
        assertThat(consumerGroupRepository.find(CONSUMER_GROUP_A)).isNotPresent();
    }

    @Test
    void find_whenNotCreated_isNotPresent() {
        assertThat(consumerGroupRepository.find("UNDEFINED")).isNotPresent();
    }

    @Test
    void findAll_whenPaging_returnsCorrectItems() {
        assertThat(consumerGroupRepository.findAll(100, 0))
                .hasSize(data.consumerGroups().size())
                .extracting(ConsumerGroup::name)
                .containsExactlyInAnyOrder(
                        CONSUMER_GROUP_A, CONSUMER_GROUP_B, CONSUMER_GROUP_C, CONSUMER_GROUP_D);
    }

    @Test
    void findAll_whenEmpty() {
        consumerRepository.findAll(100, 0).forEach(c -> consumerRepository.delete(c.id()));
        consumerGroupRepository.findAll(100, 0).forEach(s -> consumerGroupRepository.delete(s.name()));
        assertThat(consumerGroupRepository.findAll(100, 0)).isEmpty();
}
}
