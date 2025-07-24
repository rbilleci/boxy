package boxy.core.dao;

import boxy.core.DataAccessException;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BaseDao {

    @FunctionalInterface
    interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    protected final DataSource ds;

    protected BaseDao(DataSource ds) {
        this.ds = ds;
    }

    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        var list = query(sql, mapper, params);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    protected <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        return execute(sql, ps -> {
            try (final var rs = ps.executeQuery()) {
                final var result = new ArrayList<T>();
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
                return result;
            }
        }, params);
    }

    protected int update(String sql, Object... params) {
        return execute(sql, PreparedStatement::executeUpdate, params);
    }

    private <T> T execute(String sql, SQLFunction<PreparedStatement, T> f, Object... params) {
        try (var conn = ds.getConnection(); var ps = conn.prepareStatement(sql)) {
            bind(ps, params);
            return f.apply(ps);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (var i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
