package boxy.core.dao;

import boxy.core.DataAccessException;

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

    protected <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        final var list = query(sql, mapper, params);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(list.getFirst());
    }

    protected <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) throws DataAccessException {
        try (final var connection = ds.getConnection();
             final var statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            try (final var rs = statement.executeQuery()) {
                final var results = new ArrayList<T>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        } catch (final SQLException e) {
            throw new DataAccessException(e.getMessage(), e.getCause());
        }
    }

    protected int update(String sql, Object... params) throws DataAccessException {
        try (final var connection = ds.getConnection();
             final var statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            return statement.executeUpdate();
        } catch (final SQLException e) {
            throw new DataAccessException(e.getMessage(), e.getCause());
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
