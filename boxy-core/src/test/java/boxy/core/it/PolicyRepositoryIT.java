package boxy.core.it;

import boxy.core.domain.HeartbeatPolicy;
import boxy.core.domain.LeasePolicy;
import boxy.core.domain.MetricsPolicy;
import boxy.core.repository.HeartbeatPolicyRepository;
import boxy.core.repository.LeasePolicyRepository;
import boxy.core.repository.MetricsPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class PolicyRepositoryIT extends BaseIT {

    private HeartbeatPolicyRepository heartbeatPolicyRepository;
    private LeasePolicyRepository leasePolicyRepository;
    private MetricsPolicyRepository metricsPolicyRepository;

    @BeforeEach
    void setup() {
        heartbeatPolicyRepository = new HeartbeatPolicyRepository(dataSource);
        leasePolicyRepository = new LeasePolicyRepository(dataSource);
        metricsPolicyRepository = new MetricsPolicyRepository(dataSource);
    }

    @Test
    void heartbeatPolicy_updateAndGet() {
        HeartbeatPolicy current = heartbeatPolicyRepository.get();
        HeartbeatPolicy updated = new HeartbeatPolicy(2.0, 4.0, 5.0, 8.0);
        heartbeatPolicyRepository.update(updated);
        assertThat(heartbeatPolicyRepository.get()).isEqualTo(updated);
    }

    @Test
    void leasePolicy_updateAndGet() {
        LeasePolicy current = leasePolicyRepository.get();
        LeasePolicy updated = new LeasePolicy(current.activeConsumersLimit() + 1, current.leaseReleasePeriod() + 1);
        leasePolicyRepository.update(updated);
        assertThat(leasePolicyRepository.get()).isEqualTo(updated);
    }

    @Test
    void metricsPolicy_updateAndGet() {
        MetricsPolicy current = metricsPolicyRepository.get();
        MetricsPolicy updated = new MetricsPolicy(current.metricsRefreshInterval() + 1);
        metricsPolicyRepository.update(updated);
        assertThat(metricsPolicyRepository.get()).isEqualTo(updated);
    }
}
