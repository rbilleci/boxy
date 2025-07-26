package boxy.core.it;

import boxy.core.dao.*;
import boxy.core.model.Lease;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class LeaseIT extends BaseIT {

    private LeaseDao leaseDao;
    private TestData data;

    @BeforeEach
    void setup() {
        leaseDao = new LeaseDao(dataSource);
        data = TestData.seed(dataSource);
    }
/*

    @Test
    void acquire_whenNoLeaseExists_returnsAndCreatesLease() {
        // Attempt to acquire a lease for 10 seconds
        // check the return value is equal to tru (a successful lease)
        // then verify the owner
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id())).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
    }

    @Test
    void acquire_whenActiveLeaseExists_returnsAndDoesNotChangeLease() {
        // Attempt to acquire a lease for 10 seconds
        // check the return value is equal to true (a successful lease)
        // then verify the owner
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id())).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());

        // Then, another party attempts to acquire the lease,
        // the lease must be unchanged.
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker2().id())).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
    }

    @Test
    void acquire_whenExpiredLeaseExists_returnsTrueAndChangesOwner() throws InterruptedException {
        // Attempt to acquire a lease for **1** second
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id())).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
        Thread.sleep(1000);
        // Then, another party attempts to acquire the lease
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker2().id())).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker2().id());
    }

    @Test
    void delete_whenCallerIsOwner_returnsAndDeletesLease() {
        // WORKER 1 ACQUIRES LEASE
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id())).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
        // RELEASE
        leaseDao.release(data.subscriptionOffset().id(), data.worker1().id());
        assertThat(leaseDao.find(data.subscriptionOffset().id())).isNotPresent();
    }

    @Test
    void delete_whenCallerIsNotOwner_returnsAndLeaseRemains() {
        // Attempt to acquire a lease
        assertThat(leaseDao.acquire(data.subscriptionOffset().id(), data.worker1().id())).isTrue();
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId).isEqualTo(data.worker1().id());
        // RELEASE BY PARTY 2!!!
        leaseDao.release(data.subscriptionOffset().id(), data.worker2().id());
        // PARTY 1 STILL OWNS IT!!!
        assertThat(leaseDao.find(data.subscriptionOffset().id()))
                .isPresent()
                .get()
                .extracting(Lease::workerId)
                .isEqualTo(data.worker1().id());
    }

 */
}
