package boxy.core.dao;

import boxy.core.model.Partition;

import javax.sql.DataSource;
import java.util.Optional;

public class PartitionDao extends BaseDao {

    private static final RowMapper<Partition> MAPPER = rs -> new Partition(
            rs.getLong("id"),
            rs.getLong("topic_id"),
            rs.getInt("partition_number"),
            rs.getLong("high_watermark"));

    public PartitionDao(DataSource ds) {
        super(ds);
    }

    public Optional<Partition> find(long id) {
        return queryOne("SELECT * FROM partitions WHERE id = ?", MAPPER, id);
    }

    public Optional<Partition> find(long topicId, int partitionNumber) {
        return queryOne("SELECT * FROM partitions WHERE topic_id = ? AND partition_number = ?", MAPPER, topicId, partitionNumber);
    }
}
