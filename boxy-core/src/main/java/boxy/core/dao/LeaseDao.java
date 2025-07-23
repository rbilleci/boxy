package boxy.core.dao;

import boxy.core.model.Lease;
import boxy.core.model.SubscriptionOffset;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(Lease.class)
public interface LeaseDao extends SqlObject {

    @SqlQuery("SELECT * FROM leases WHERE subscription_offset_id = :subscriptionOffsetId")
    Optional<Lease> find(@Bind("subscriptionOffsetId") long subscriptionOffsetId);

    @SqlQuery("SELECT * FROM leases_available_view LIMIT :limit OFFSET :offset")
    @RegisterConstructorMapper(SubscriptionOffset.class)
    List<SubscriptionOffset> leasesAvailable(@Bind("limit") int limit,
                                             @Bind("offset") int offset);


    default boolean acquire(long subscriptionOffsetId, long workerId, long expiresAfter) {
        return getHandle().createQuery("CALL sp_leases_acquire(:so,:worker,:exp)")
                .bind("so", subscriptionOffsetId)
                .bind("worker", workerId)
                .bind("exp", expiresAfter)
                .mapTo(Integer.class)
                .one() > 0;
    }

    default boolean renew(long subscriptionOffsetId, long workerId, long expiresAfter) {
        return getHandle().createQuery("CALL sp_leases_renew(:so,:worker,:exp)")
                .bind("so", subscriptionOffsetId)
                .bind("worker", workerId)
                .bind("exp", expiresAfter)
                .mapTo(Integer.class)
                .one() > 0;
    }

    default void release(long subscriptionOffsetId, long workerId) {
        getHandle().createUpdate("CALL sp_leases_release(:so,:worker)")
                .bind("so", subscriptionOffsetId)
                .bind("worker", workerId)
                .execute();
    }

}
