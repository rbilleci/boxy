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

    public Optional<Partition> find(String path, String topic, int partitionNumber) {
        return queryOne("""
                        WITH ns AS (
                            SELECT nc.descendant_id AS id,
                                   GROUP_CONCAT(a.name ORDER BY nc.depth DESC SEPARATOR '/') AS path
                              FROM namespace_closure nc
                              JOIN namespaces a ON a.id = nc.ancestor_id
                             GROUP BY nc.descendant_id
                        )
                        SELECT p.*
                          FROM partitions p
                          JOIN topics t ON p.topic_id = t.id
                          JOIN ns ON ns.id = t.namespace_id
                         WHERE ns.path = ?
                           AND t.name = ?
                           AND p.partition_number = ?
                        """,
                PARTITION_MAPPER,
                path, topic, partitionNumber);
    }

}
