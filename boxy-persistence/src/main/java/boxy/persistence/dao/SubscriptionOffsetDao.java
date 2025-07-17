package boxy.persistence.dao;

import boxy.persistence.model.SubscriptionOffset;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(SubscriptionOffset.class)
public interface SubscriptionOffsetDao {

    @SqlUpdate("UPDATE subscription_offsets SET committed_offset = :offset WHERE id = :id AND committed_offset < :offset")
    void commit(@Bind("id") long id, @Bind("offset") long offset);

    @SqlQuery("SELECT * FROM subscription_offsets_view WHERE id = :id")
    Optional<SubscriptionOffset> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM subscription_offsets_view WHERE subscription_id = :subscriptionId AND partition_id = :partitionId")
    Optional<SubscriptionOffset> find(@Bind("subscriptionId") long subscriptionId, @Bind("partitionId") long partitionId);

    @SqlQuery("SELECT * FROM subscription_offsets_view WHERE subscription_id = :subscriptionId ORDER BY id")
    List<SubscriptionOffset> findAll(@Bind("subscriptionId") long subscriptionId);

}
