package boxy.core.mapper;

import boxy.core.domain.CheckInResult;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

public class CheckInResultMapper implements RowMapper<CheckInResult> {
    @Override
    public CheckInResult map(ResultSet rs) throws SQLException {
        return new CheckInResult(
                rs.getDouble("heartbeat_interval"),
                rs.getTimestamp("heartbeat_deadline").toInstant(),
                CheckInResult.Status.valueOf(rs.getString("status")),
                new ArrayList<>());
    }
}
