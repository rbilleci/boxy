package boxy.core.it;

import boxy.core.repository.ConsumerGroupRepository;
import boxy.core.domain.ConsumerGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ConsumerGroupIT extends BaseIT {

    private ConsumerGroupRepository consumerGroupRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        consumerGroupRepository = new ConsumerGroupRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void create_whenCreated_isPresent() {
        System.out.println("[DEBUG_LOG] Running create_whenCreated_isPresent test");
        final var consumerGroup = consumerGroupRepository.find(TENANT_1, CONSUMER_GROUP_A).orElseThrow();
        System.out.println("[DEBUG_LOG] Found consumer group: " + consumerGroup);
        System.out.println("[DEBUG_LOG] Consumer group fields:");
        System.out.println("[DEBUG_LOG]   ID: " + consumerGroup.id());
        System.out.println("[DEBUG_LOG]   Tenant: " + consumerGroup.tenant());
        System.out.println("[DEBUG_LOG]   Name: " + consumerGroup.name());
        System.out.println("[DEBUG_LOG]   Heartbeat Interval Default: " + consumerGroup.heartbeatIntervalBaseline());
        System.out.println("[DEBUG_LOG]   Heartbeat Deadline Multiplier: " + consumerGroup.heartbeatDeadlineMultiplier());
        System.out.println("[DEBUG_LOG]   Lease Release Period: " + consumerGroup.leaseReleasePeriod());
        System.out.println("[DEBUG_LOG]   Heartbeat QPS Target: " + consumerGroup.heartbeatTargetQPS());
        System.out.println("[DEBUG_LOG]   Heartbeat Interval Limit: " + consumerGroup.heartbeatIntervalLimit());
        System.out.println("[DEBUG_LOG]   Active Workers Count: " + consumerGroup.activeWorkers());
        System.out.println("[DEBUG_LOG]   Total Weight: " + consumerGroup.activeWorkersWeight());
        System.out.println("[DEBUG_LOG]   Active Partitions Count: " + consumerGroup.activePartitions());
        System.out.println("[DEBUG_LOG]   Last Updated: " + consumerGroup.lastUpdated());
        assertThat(consumerGroup).extracting(ConsumerGroup::tenant).isEqualTo(TENANT_1);
        assertThat(consumerGroup).extracting(ConsumerGroup::name).isEqualTo(CONSUMER_GROUP_A);
    }

    @Test
    void deleted_whenDeleted_isNotPresent() {
        consumerGroupRepository.find(TENANT_1, CONSUMER_GROUP_A).orElseThrow();
        consumerGroupRepository.delete(TENANT_1, CONSUMER_GROUP_A);
        assertThat(consumerGroupRepository.find(TENANT_1, CONSUMER_GROUP_A)).isNotPresent();
    }

    @Test
    void find_whenNotCreated_isNotPresent() {
        assertThat(consumerGroupRepository.find("UNDEFINED", "UNDEFINED")).isNotPresent();
    }

    @Test
    void findAll_whenPaging_returnsCorrectItems() {
        assertThat(consumerGroupRepository.findAll(100, 0))
                .hasSize(data.consumerGroups().size())
                .extracting(ConsumerGroup::name)
                .containsExactlyInAnyOrder(
                        CONSUMER_GROUP_A, CONSUMER_GROUP_B, CONSUMER_GROUP_C, CONSUMER_GROUP_D,
                        CONSUMER_GROUP_A, CONSUMER_GROUP_B, CONSUMER_GROUP_C, CONSUMER_GROUP_D);
        assertThat(consumerGroupRepository.findAll(100, 0))
                .hasSize(data.consumerGroups().size())
                .extracting(ConsumerGroup::tenant)
                .containsExactlyInAnyOrder(
                        TENANT_1, TENANT_1, TENANT_1, TENANT_1,
                        TENANT_2, TENANT_2, TENANT_2, TENANT_2);
    }

    @Test
    void findAll_whenEmpty() {
        consumerGroupRepository.findAll(100, 0).forEach(cg -> consumerGroupRepository.delete(cg.tenant(), cg.name()));
        assertThat(consumerGroupRepository.findAll(100, 0)).isEmpty();
    }
}
