package boxy.persistence.dao;

import boxy.persistence.model.Worker;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.List;
import java.util.Optional;

@RegisterConstructorMapper(Worker.class)
public interface WorkerDao extends SqlObject {

    default long register(String nodeId, long consumerGroupId, int weight) {
        return getHandle().createQuery("CALL sp_workers_register(:nodeId,:consumerGroupId,:weight)")
                .bind("nodeId", nodeId)
                .bind("consumerGroupId", consumerGroupId)
                .bind("weight", weight)
                .mapTo(Long.class)
                .one();
    }

    default void deregister(long id) {
        getHandle().createUpdate("CALL sp_workers_deregister(:id)")
                .bind("id", id)
                .execute();
    }

    @SqlQuery("SELECT * FROM workers WHERE id = :id")
    Optional<Worker> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM workers WHERE node_id = :nodeId AND consumer_group_id = :consumerGroupId")
    Optional<Worker> find(@Bind("nodeId") String nodeId,
                          @Bind("consumerGroupId") long consumerGroupId);

}
