package boxy.persistence.it;

import boxy.persistence.dao.ConsumerGroupDao;
import boxy.persistence.model.ConsumerGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class ConsumerGroupIT extends BaseIT {

    private static final String TENANT_1 = "t-1";
    private static final String TENANT_2 = "t-2";
    private static final String CONSUMER_GROUP_A = "g-a";
    private static final String CONSUMER_GROUP_B = "g-b";
    private static final String CONSUMER_GROUP_C = "g-c";
    private static final String CONSUMER_GROUP_D = "g-d";

    private ConsumerGroupDao consumerGroupDao;

    @BeforeEach
    void setup() {
        consumerGroupDao = jdbi.onDemand(ConsumerGroupDao.class);
    }

    @Test
    void create_whenCreated_isPresent() {
        final var consumerGroupId = consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_A);
        final var consumerGroup = consumerGroupDao.find(consumerGroupId);
        assertThat(consumerGroup).isPresent().get().extracting(ConsumerGroup::id).isEqualTo(consumerGroupId);
        assertThat(consumerGroup).isPresent().get().extracting(ConsumerGroup::tenant).isEqualTo(TENANT_1);
        assertThat(consumerGroup).isPresent().get().extracting(ConsumerGroup::name).isEqualTo(CONSUMER_GROUP_A);
        assertThat(consumerGroupDao.find(TENANT_1, CONSUMER_GROUP_A)).isPresent()
                .get()
                .extracting(ConsumerGroup::id)
                .isEqualTo(consumerGroupId);
    }

    @Test
    void deleted_whenDeleted_isNotPresent() {
        final var consumerGroupId = consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_A);
        final var consumerGroup = consumerGroupDao.find(consumerGroupId);
        assertThat(consumerGroup).isPresent().get().extracting(ConsumerGroup::id).isEqualTo(consumerGroupId);
        consumerGroupDao.delete(consumerGroupId);
        assertThat(consumerGroupDao.find(consumerGroupId)).isNotPresent();
    }

    @Test
    void find_whenNotCreated_isNotPresent() {
        assertThat(consumerGroupDao.find(TENANT_1, CONSUMER_GROUP_A)).isNotPresent();
    }

    @Test
    void findAll_whenPaging_returnsCorrectItems() {
        consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_A);
        consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_B);
        consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_C);
        consumerGroupDao.create(TENANT_1, CONSUMER_GROUP_D);
        consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_A);
        consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_B);
        consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_C);
        consumerGroupDao.create(TENANT_2, CONSUMER_GROUP_D);
        assertThat(consumerGroupDao.findAll(100, 0))
                .hasSize(8)
                .extracting(ConsumerGroup::name)
                .containsExactlyInAnyOrder(
                        CONSUMER_GROUP_A, CONSUMER_GROUP_B, CONSUMER_GROUP_C, CONSUMER_GROUP_D,
                        CONSUMER_GROUP_A, CONSUMER_GROUP_B, CONSUMER_GROUP_C, CONSUMER_GROUP_D);
        assertThat(consumerGroupDao.findAll(100, 0))
                .hasSize(8)
                .extracting(ConsumerGroup::tenant)
                .containsExactlyInAnyOrder(
                        TENANT_1, TENANT_1, TENANT_1, TENANT_1,
                        TENANT_2, TENANT_2, TENANT_2, TENANT_2);
    }

    @Test
    void findAll_whenEmpty() {
        assertThat(consumerGroupDao.findAll(100, 0)).isEmpty();
    }
}
