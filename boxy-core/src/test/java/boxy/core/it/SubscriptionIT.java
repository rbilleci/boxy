package boxy.core.it;

import boxy.core.repository.SubscriptionRepository;
import boxy.core.domain.Subscription;
import boxy.core.repository.WorkerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static boxy.core.it.TestData.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class SubscriptionIT extends BaseIT {

    private SubscriptionRepository subscriptionRepository;
    private WorkerRepository workerRepository;
    private TestData data;

    @BeforeEach
    void setup() {
        subscriptionRepository = new SubscriptionRepository(dataSource);
        workerRepository = new WorkerRepository(dataSource);
        data = TestData.seed(dataSource);
    }

    @Test
    void create_whenCreated_isPresent() {
        System.out.println("[DEBUG_LOG] Running create_whenCreated_isPresent test");
        final var subscription = subscriptionRepository.find(SUBSCRIPTION_A).orElseThrow();
        System.out.println("[DEBUG_LOG] Found subscription: " + subscription);
        System.out.println("[DEBUG_LOG] Subscription fields:");
        System.out.println("[DEBUG_LOG]   ID: " + subscription.id());
        System.out.println("[DEBUG_LOG]   Name: " + subscription.name());
        System.out.println("[DEBUG_LOG]   Heartbeat Interval Default: " + subscription.heartbeatIntervalBaseline());
        System.out.println("[DEBUG_LOG]   Heartbeat Deadline Multiplier: " + subscription.heartbeatDeadlineMultiplier());
        System.out.println("[DEBUG_LOG]   Lease Release Period: " + subscription.leaseReleasePeriod());
        System.out.println("[DEBUG_LOG]   Heartbeat QPS Target: " + subscription.heartbeatTargetQPS());
        System.out.println("[DEBUG_LOG]   Heartbeat Interval Limit: " + subscription.heartbeatIntervalLimit());
        System.out.println("[DEBUG_LOG]   Active Workers Count: " + subscription.activeWorkers());
        System.out.println("[DEBUG_LOG]   Total Weight: " + subscription.activeWorkersWeight());
        System.out.println("[DEBUG_LOG]   Active Partitions Count: " + subscription.activePartitions());
        System.out.println("[DEBUG_LOG]   Last Modified At: " + subscription.lastModifiedAt());
        assertThat(subscription).extracting(Subscription::name).isEqualTo(SUBSCRIPTION_A);
    }

    @Test
    void deleted_whenDeleted_isNotPresent() {
        subscriptionRepository.find(SUBSCRIPTION_A).orElseThrow();
        workerRepository.findAll(100, 0).forEach(w -> workerRepository.delete(w.id()));
        subscriptionRepository.delete(SUBSCRIPTION_A);
        assertThat(subscriptionRepository.find(SUBSCRIPTION_A)).isNotPresent();
    }

    @Test
    void find_whenNotCreated_isNotPresent() {
        assertThat(subscriptionRepository.find("UNDEFINED")).isNotPresent();
    }

    @Test
    void findAll_whenPaging_returnsCorrectItems() {
        assertThat(subscriptionRepository.findAll(100, 0))
                .hasSize(data.subscriptions().size())
                .extracting(Subscription::name)
                .containsExactlyInAnyOrder(
                        SUBSCRIPTION_A, SUBSCRIPTION_B, SUBSCRIPTION_C, SUBSCRIPTION_D);
    }

    @Test
    void findAll_whenEmpty() {
        workerRepository.findAll(100, 0).forEach(w -> workerRepository.delete(w.id()));
        subscriptionRepository.findAll(100, 0).forEach(s -> subscriptionRepository.delete(s.name()));
        assertThat(subscriptionRepository.findAll(100, 0)).isEmpty();
    }
}
