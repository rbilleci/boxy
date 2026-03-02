package boxy.core.repository;

import boxy.core.DataAccessException;
import boxy.core.mapper.RowMapper;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for all Boxy JDBC repositories.
 *
 * <p>Provides lightweight helpers for executing parameterised SQL and mapping result sets to
 * domain records. All SQL execution goes through the private {@code execute} method;
 * subclasses never interact with {@link Connection} or {@link PreparedStatement} directly.
 *
 * <p>Every {@link SQLException} is wrapped in a {@link DataAccessException} and re-thrown.
 * Callers can inspect {@link DataAccessException#getSqlState()},
 * {@link DataAccessException#getVendorCode()}, and
 * {@link DataAccessException#hasErrorCode(String)} to distinguish stored-procedure error
 * signals from infrastructure failures.
 */
public abstract class BaseRepository {

    /**
     * Functional interface for operations applied to a {@link PreparedStatement} that may
     * throw {@link SQLException}.
     */
    @FunctionalInterface
    interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    /** The data source used by this repository; injected at construction time. */
    protected final DataSource ds;

    /**
     * Constructs a repository backed by the given data source.
     *
     * @param ds the data source; must not be {@code null}
     */
    protected BaseRepository(final DataSource ds) {
        this.ds = ds;
    }

    /**
     * Executes a query and returns at most one mapped result.
     *
     * @param <T>        the domain type
     * @param sql        SQL string or {@code {CALL …}} escape for stored procedures
     * @param mapper     applied to the first row of the result set
     * @param parameters positional bind parameters
     * @return {@link Optional#empty()} if the result set is empty; otherwise the mapped first row
     * @throws DataAccessException if any SQL error occurs
     */
    protected <T> Optional<T> queryOne(final String sql, final RowMapper<T> mapper, final Object... parameters) {
        return execute(sql, ps -> {
            try (final var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();
            }
        }, parameters);
    }

    /**
     * Executes a query and returns all mapped rows as a list.
     *
     * @param <T>        the domain type
     * @param sql        SQL string or {@code {CALL …}} escape for stored procedures
     * @param mapper     applied to each row
     * @param parameters positional bind parameters
     * @return an ordered list of mapped rows; never {@code null}, may be empty
     * @throws DataAccessException if any SQL error occurs
     */
    protected <T> List<T> query(final String sql, final RowMapper<T> mapper, final Object... parameters) {
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

    /**
     * Executes a DML statement or stored-procedure call and returns the update count.
     *
     * @param sql        SQL string or {@code {CALL …}} escape for stored procedures
     * @param parameters positional bind parameters
     * @return the number of rows affected
     * @throws DataAccessException if any SQL error occurs
     */
    protected int update(final String sql, final Object... parameters) {
        return execute(sql, PreparedStatement::executeUpdate, parameters);
    }

    /**
     * Core execution helper: obtains a connection, prepares the statement, binds parameters,
     * delegates to {@code fn}, and closes all resources.
     *
     * @param <T>        the return type of {@code fn}
     * @param sql        the SQL string to prepare
     * @param fn         the operation to apply to the prepared statement
     * @param parameters positional bind parameters
     * @return the result of {@code fn}
     * @throws DataAccessException wrapping any {@link SQLException} thrown
     */
    private <T> T execute(final String sql, final SQLFunction<PreparedStatement, T> fn, final Object... parameters) {
        try (final var connection = ds.getConnection();
             final var statement = connection.prepareStatement(sql)) {
            for (var i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            return fn.apply(statement);
        } catch (SQLException e) {
            throw new DataAccessException(e);
        }
    }

}
