package boxy.core.dao;

import boxy.core.DataAccessException;
import boxy.core.mapper.RowMapper;

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
        return execute(sql, ps -> {
            try (final var rs = ps.executeQuery()) {
                return rs.next() ?
                        Optional.of(mapper.map(rs)) :
                        Optional.empty();
            }
        }, params);
    }

    protected <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        return execute(sql, ps -> {
            try (final var rs = ps.executeQuery()) {
                final var results = new ArrayList<T>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        }, params);
    }

    protected int update(String sql, Object... parameters) {
        return execute(sql, PreparedStatement::executeUpdate, parameters);
    }

    private <T> T execute(final String sql,
                          final SQLFunction<PreparedStatement, T> function,
                          final Object... parameters) {
        try (final var connection = ds.getConnection();
             final var statement = connection.prepareStatement(sql)) {
            for (var i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return function.apply(statement);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

}
