package boxy.core.dao;

import boxy.core.model.ConsumerGroup;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(ConsumerGroup.class)
public interface ConsumerGroupDao extends SqlObject {

    default long create(String tenant, String name) {
        return getHandle().createQuery("CALL sp_consumer_groups_create(:tenant,:name)")
                .bind("tenant", tenant)
                .bind("name", name)
                .mapTo(Long.class)
                .one();
    }

    default void delete(long id) {
        getHandle().createUpdate("CALL sp_consumer_groups_delete(:id)")
                .bind("id", id)
                .execute();
    }

    @SqlQuery("SELECT * FROM consumer_groups WHERE id = :id")
    Optional<ConsumerGroup> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM consumer_groups WHERE tenant = :tenant AND name = :name")
    Optional<ConsumerGroup> find(@Bind("tenant") String tenant, @Bind("name") String name);

    @SqlQuery("SELECT * FROM consumer_groups ORDER BY id LIMIT :limit OFFSET :offset")
    List<ConsumerGroup> findAll(@Bind("limit") int limit, @Bind("offset") int offset);
}
