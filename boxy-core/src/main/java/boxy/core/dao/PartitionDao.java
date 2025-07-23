package boxy.core.dao;

import boxy.core.model.Partition;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.util.Optional;

@RegisterConstructorMapper(Partition.class)
public interface PartitionDao {

    @SqlQuery("SELECT * FROM partitions WHERE id = :id")
    Optional<Partition> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM partitions WHERE topic_id = :topicId AND partition_number = :partitionNumber")
    Optional<Partition> find(@Bind("topicId") long topicId, @Bind("partitionNumber") int partitionNumber);

}
