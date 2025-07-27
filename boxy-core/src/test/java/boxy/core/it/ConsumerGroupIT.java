package boxy.core.it;

import boxy.core.dao.ConsumerGroupDao;
import boxy.core.model.ConsumerGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ConsumerGroupIT extends BaseIT {

    private ConsumerGroupDao consumerGroupDao;
    private TestData data;

    @BeforeEach
    void setup() {
        consumerGroupDao = new ConsumerGroupDao(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void create_whenCreated_isPresent() {
        System.out.println("[DEBUG_LOG] Running create_whenCreated_isPresent test");
        final var consumerGroup = consumerGroupDao.find(TENANT_1, CONSUMER_GROUP_A).orElseThrow();
        System.out.println("[DEBUG_LOG] Found consumer group: " + consumerGroup);
        System.out.println("[DEBUG_LOG] Consumer group fields:");
        System.out.println("[DEBUG_LOG]   ID: " + consumerGroup.id());
        System.out.println("[DEBUG_LOG]   Tenant: " + consumerGroup.tenant());
        System.out.println("[DEBUG_LOG]   Name: " + consumerGroup.name());
        System.out.println("[DEBUG_LOG]   Heartbeat Interval Default: " + consumerGroup.heartbeatIntervalDefault());
        System.out.println("[DEBUG_LOG]   Heartbeat Deadline Multiplier: " + consumerGroup.heartbeatDeadlineMultiplier());
        System.out.println("[DEBUG_LOG]   Release Deadline: " + consumerGroup.releaseDeadline());
        System.out.println("[DEBUG_LOG]   Active Workers Count: " + consumerGroup.activeWorkers());
        System.out.println("[DEBUG_LOG]   Total Weight: " + consumerGroup.totalWeight());
        System.out.println("[DEBUG_LOG]   Active Partitions Count: " + consumerGroup.activePartitions());
        System.out.println("[DEBUG_LOG]   Last Updated: " + consumerGroup.lastUpdated());
        assertThat(consumerGroup).extracting(ConsumerGroup::tenant).isEqualTo(TENANT_1);
        assertThat(consumerGroup).extracting(ConsumerGroup::name).isEqualTo(CONSUMER_GROUP_A);
    }

    @Test
    void deleted_whenDeleted_isNotPresent() {
        consumerGroupDao.find(TENANT_1, CONSUMER_GROUP_A).orElseThrow();
        consumerGroupDao.delete(TENANT_1, CONSUMER_GROUP_A);
        assertThat(consumerGroupDao.find(TENANT_1, CONSUMER_GROUP_A)).isNotPresent();
    }

    @Test
    void find_whenNotCreated_isNotPresent() {
        assertThat(consumerGroupDao.find("UNDEFINED", "UNDEFINED")).isNotPresent();
    }

    @Test
    void findAll_whenPaging_returnsCorrectItems() {
        assertThat(consumerGroupDao.findAll(100, 0))
                .hasSize(data.consumerGroups().size())
                .extracting(ConsumerGroup::name)
                .containsExactlyInAnyOrder(
                        CONSUMER_GROUP_A, CONSUMER_GROUP_B, CONSUMER_GROUP_C, CONSUMER_GROUP_D,
                        CONSUMER_GROUP_A, CONSUMER_GROUP_B, CONSUMER_GROUP_C, CONSUMER_GROUP_D);
        assertThat(consumerGroupDao.findAll(100, 0))
                .hasSize(data.consumerGroups().size())
                .extracting(ConsumerGroup::tenant)
                .containsExactlyInAnyOrder(
                        TENANT_1, TENANT_1, TENANT_1, TENANT_1,
                        TENANT_2, TENANT_2, TENANT_2, TENANT_2);
    }

    @Test
    void findAll_whenEmpty() {
        consumerGroupDao.findAll(100, 0).forEach(cg -> consumerGroupDao.delete(cg.tenant(), cg.name()));
        assertThat(consumerGroupDao.findAll(100, 0)).isEmpty();
    }
}
