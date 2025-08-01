package boxy.core.repository;

import boxy.core.mapper.PartitionMapper;
import boxy.core.domain.Partition;

import javax.sql.DataSource;
import java.util.Optional;

public final class PartitionRepository extends BaseRepository {

    private static final PartitionMapper PARTITION_MAPPER = new PartitionMapper();

    public PartitionRepository(DataSource ds) {
        super(ds);
    }

    public Optional<Partition> find(long namespaceId, String topic, int partitionNumber) {
        return queryOne("""
                        SELECT * FROM partitions p JOIN topics t ON p.topic_id = t.id
                        WHERE t.namespace_id = ? AND t.name = ? AND p.partition_number = ?
                        """,
                PARTITION_MAPPER,
                namespaceId, topic, partitionNumber);
    }

}
