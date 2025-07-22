package boxy.persistence.dao;

import boxy.persistence.model.ConsumerGroup;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(ConsumerGroup.class)
public interface ConsumerGroupDao {

    @SqlUpdate("INSERT INTO consumer_groups (tenant, name) VALUES (:tenant, :name)")
    @GetGeneratedKeys
    long create(@Bind("tenant") String tenant, @Bind("name") String name);

    @SqlUpdate("DELETE FROM consumer_groups WHERE id = :id")
    void delete(@Bind("id") long id);

    @SqlQuery("SELECT * FROM consumer_groups WHERE id = :id")
    Optional<ConsumerGroup> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM consumer_groups WHERE tenant = :tenant AND name = :name")
    Optional<ConsumerGroup> find(@Bind("tenant") String tenant, @Bind("name") String name);

    @SqlQuery("SELECT * FROM consumer_groups ORDER BY id LIMIT :limit OFFSET :offset")
    List<ConsumerGroup> findAll(@Bind("limit") int limit, @Bind("offset") int offset);
}
