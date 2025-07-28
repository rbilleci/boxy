package boxy.core.mapper;

import boxy.core.model.Partition;

import java.sql.ResultSet;
import java.sql.SQLException;

public class PartitionMapper implements RowMapper<Partition> {
    @Override
    public Partition map(ResultSet rs) throws SQLException {
        return new Partition(
                rs.getLong("id"),
                rs.getLong("topic_id"),
                rs.getInt("partition_number"),
                rs.getLong("high_watermark"));
    }
}
