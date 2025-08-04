package boxy.core.repository;

import boxy.core.mapper.PartitionMapper;
import boxy.core.domain.Partition;

import javax.sql.DataSource;
import java.util.Optional;

public final class PartitionRepository extends BaseRepository {

    private static final PartitionMapper PARTITION_MAPPER = new PartitionMapper();

    public PartitionRepository(final DataSource ds) {
        super(ds);
    }

    public Optional<Partition> find(final String path, final String topic, final int partitionNumber) {
        return queryOne("""
                SELECT p.*
                  FROM partitions p
                  JOIN topics t ON p.topic_id = t.id
                  JOIN namespaces n ON n.id = t.namespace_id
                 WHERE n.path_hash       = UNHEX(MD5(?))
                   AND n.path            = ?
                   AND t.name            = ?
                   AND p.partition_number = ?
                """, PARTITION_MAPPER, path, path, topic, partitionNumber);
    }

}
