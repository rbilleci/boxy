package boxy.core.repository;

import boxy.core.DataAccessException;
import boxy.core.mapper.RowMapper;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BaseRepository {

    @FunctionalInterface
    interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    protected final DataSource ds;

    protected BaseRepository(DataSource ds) {
        this.ds = ds;
    }

    protected <T> Optional<T> queryOne(final String sql,
                                       final RowMapper<T> mapper,
                                       final Object... parameters) {
        return execute(sql, ps -> {
            try (final var rs = ps.executeQuery()) {
                return rs.next() ?
                        Optional.of(mapper.map(rs)) :
                        Optional.empty();
            }
        }, parameters);
    }

    protected <T> List<T> query(final String sql,
                                final RowMapper<T> mapper,
                                final Object... parameters) {
        return execute(sql, ps -> {
            try (final var rs = ps.executeQuery()) {
                final var results = new ArrayList<T>();
                while (rs.next()) {
                    results.add(mapper.map(rs));
                }
                return results;
            }
        }, parameters);
    }

    protected int update(final String sql,
                         final Object... parameters) {
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
