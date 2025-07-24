package boxy.core.dao;

import boxy.core.jdbc.BaseDao;
import boxy.core.jdbc.RowMapper;
import boxy.core.model.Partition;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Optional;

public class PartitionDao extends BaseDao {
    private static final RowMapper<Partition> MAPPER = rs -> new Partition(
            rs.getLong("id"),
            rs.getLong("topic_id"),
            rs.getInt("partition_number"),
            rs.getLong("high_watermark")
    );

    public PartitionDao(DataSource ds) {
        super(ds);
    }

    public Optional<Partition> find(long id) throws SQLException {
        return queryOne("SELECT * FROM partitions WHERE id = ?", MAPPER, id);
    }

    public Optional<Partition> find(long topicId, int partitionNumber) throws SQLException {
        return queryOne("SELECT * FROM partitions WHERE topic_id = ? AND partition_number = ?", MAPPER, topicId, partitionNumber);
    }
}
