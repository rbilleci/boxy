package boxy.persistence.it;

import boxy.persistence.dao.ConsumerGroupDao;
import boxy.persistence.model.ConsumerGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.persistence.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ConsumerGroupIT extends BaseIT {

    private ConsumerGroupDao consumerGroupDao;
    private TestData data;

    @BeforeEach
    void setup() {
        consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
        data = TestData.seed(jdbi);
    }

    @Test
    void create_whenCreated_isPresent() {
        final var consumerGroup = consumerGroupDao.find(TENANT_1, CONSUMER_GROUP_A).orElseThrow();
        assertThat(consumerGroup).extracting(ConsumerGroup::tenant).isEqualTo(TENANT_1);
        assertThat(consumerGroup).extracting(ConsumerGroup::name).isEqualTo(CONSUMER_GROUP_A);
    }

    @Test
    void deleted_whenDeleted_isNotPresent() {
        final var consumerGroup = consumerGroupDao.find(TENANT_1, CONSUMER_GROUP_A).orElseThrow();
        consumerGroupDao.delete(consumerGroup.id());
        assertThat(consumerGroupDao.find(consumerGroup.id())).isNotPresent();
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
        consumerGroupDao.findAll(100, 0).forEach(cg -> consumerGroupDao.delete(cg.id()));
        assertThat(consumerGroupDao.findAll(100, 0)).isEmpty();
    }
}
