package boxy.core.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

public class IdMapper implements RowMapper<Long> {
    @Override
    public Long map(ResultSet rs) throws SQLException {
        return rs.getLong(1);
    }
}

