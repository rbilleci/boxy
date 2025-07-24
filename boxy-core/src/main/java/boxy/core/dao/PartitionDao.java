package boxy.core.dao;

import boxy.core.model.Partition;

import javax.sql.DataSource;
import java.util.Optional;

public final class PartitionDao extends BaseDao {

    private static final RowMapper<Partition> MAPPER = rs -> new Partition(
            rs.getLong("id"),
            rs.getLong("topic_id"),
            rs.getInt("partition_number"),
            rs.getLong("high_watermark"));

    public PartitionDao(DataSource ds) {
        super(ds);
    }

    public Optional<Partition> find(String tenant, String topic, int partitionNumber) {
        return queryOne("""
                SELECT * FROM partitions p JOIN topics t ON p.topic_id = t.id
                WHERE t.tenant = ? AND t.name = ? AND p.partition_number = ?
                """, MAPPER, tenant, topic, partitionNumber);
    }

}
