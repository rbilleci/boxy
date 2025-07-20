package boxy.persistence.dao;

import boxy.persistence.model.Lease;
import boxy.persistence.model.SubscriptionOffset;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(Lease.class)
public interface LeaseDao {

    @SqlQuery("SELECT * FROM leases WHERE subscription_offset_id = :subscriptionOffsetId")
    Optional<Lease> find(@Bind("subscriptionOffsetId") long subscriptionOffsetId);

    @SqlQuery("SELECT * FROM leases_available_view LIMIT :limit OFFSET :offset")
    @RegisterConstructorMapper(SubscriptionOffset.class)
    List<SubscriptionOffset> leasesAvailable(@Bind("limit") int limit,
                                             @Bind("offset") int offset);


    @SqlUpdate("""
            INSERT INTO leases (subscription_offset_id, worker_id, expires_at)
            VALUES (:subscriptionOffsetId, :workerId, CURRENT_TIMESTAMP(3) + INTERVAL :expiresAfter SECOND)
            ON DUPLICATE KEY UPDATE
                worker_id    = IF (leases.expires_at < CURRENT_TIMESTAMP(3), VALUES(worker_id), leases.worker_id),
                version     = IF (leases.expires_at < CURRENT_TIMESTAMP(3), leases.version + 1, leases.version),
                expires_at  = IF (leases.expires_at < CURRENT_TIMESTAMP(3), VALUES(expires_at), leases.expires_at)
            """
    )
    boolean acquire(@Bind("subscriptionOffsetId") long subscriptionOffsetId,
                    @Bind("workerId") long workerId,
                    @Bind("expiresAfter") long expiresAfter);

    @SqlUpdate("""
            UPDATE leases SET
                version = version + 1,
                expires_at = CURRENT_TIMESTAMP(3) + INTERVAL :expiresAfter SECOND
            WHERE
                subscription_offset_id = :subscriptionOffsetId AND
                worker_id = :workerId
            """)
    boolean renew(@Bind("subscriptionOffsetId") long subscriptionOffsetId,
                  @Bind("workerId") long workerId,
                  @Bind("expiresAfter") long expiresAfter);

    @SqlUpdate("DELETE FROM leases WHERE subscription_offset_id = :subscriptionOffsetId AND worker_id = :workerId")
    void release(@Bind("subscriptionOffsetId") long subscriptionOffsetId,
                 @Bind("workerId") long workerId);


}
