package boxy.core.jdbc;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BaseDao {
    protected final DataSource ds;

    protected BaseDao(DataSource ds) {
        this.ds = ds;
    }

    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        var list = query(sql, mapper, params);
        if (list.isEmpty()) return Optional.empty();
        return Optional.of(list.get(0));
    }

    protected <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        }
    }

    protected int update(String sql, Object... params) throws SQLException {
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
