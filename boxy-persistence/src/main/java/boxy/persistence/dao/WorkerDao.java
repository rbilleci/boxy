package boxy.persistence.dao;

import boxy.persistence.model.Worker;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(Worker.class)
public interface WorkerDao {


    @SqlUpdate("INSERT INTO workers (node_id, consumer_group_id, weight) VALUES (:nodeId, :consumerGroupId, :weight)")
    @GetGeneratedKeys
    long register(@Bind("nodeId") String nodeId,
                  @Bind("consumerGroupId") long consumerGroupId,
                  @Bind("weight") int weight);

    @SqlUpdate("DELETE FROM workers WHERE id = :id")
    void deregister(@Bind("id") long id);

    @SqlQuery("SELECT * FROM workers WHERE id = :id")
    Optional<Worker> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM workers WHERE node_id = :nodeId AND consumer_group_id = :consumerGroupId")
    Optional<Worker> find(@Bind("nodeId") String nodeId,
                          @Bind("consumerGroupId") long consumerGroupId);

    @SqlQuery("SELECT * FROM workers ORDER BY id LIMIT :limit OFFSET :offset")
    List<Worker> findAll(@Bind("limit") int limit,
                         @Bind("offset") int offset);

}
