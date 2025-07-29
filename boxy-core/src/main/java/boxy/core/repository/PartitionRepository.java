package boxy.core.repository;

import boxy.core.mapper.PartitionMapper;
import boxy.core.model.Partition;

import javax.sql.DataSource;
import java.util.Optional;

public final class PartitionRepository extends BaseRepository {

    private static final PartitionMapper PARTITION_MAPPER = new PartitionMapper();

    public PartitionRepository(DataSource ds) {
        super(ds);
    }

    public Optional<Partition> find(String tenant, String topic, int partitionNumber) {
        return queryOne("""
                        SELECT * FROM partitions p JOIN topics t ON p.topic_id = t.id
                        WHERE t.tenant = ? AND t.name = ? AND p.partition_number = ?
                        """,
                PARTITION_MAPPER,
                tenant, topic, partitionNumber);
    }

}
