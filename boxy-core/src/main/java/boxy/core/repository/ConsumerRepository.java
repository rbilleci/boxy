package boxy.core.repository;

import boxy.core.mapper.CursorMapper;
import boxy.core.mapper.CheckInResultMapper;
import boxy.core.mapper.ConsumerMapper;
import boxy.core.domain.Consumer;
import boxy.core.domain.CheckInResult;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class ConsumerRepository extends BaseRepository {

    private static final ConsumerMapper CONSUMER_MAPPER = new ConsumerMapper();
    private static final CheckInResultMapper CHECK_IN_RESULT_MAPPER = new CheckInResultMapper();
    private static final CursorMapper CURSOR_MAPPER = new CursorMapper();

    public ConsumerRepository(DataSource ds) {
        super(ds);
    }

    public Optional<Consumer> find(String id) {
        return queryOne("SELECT * FROM consumers WHERE id = ?", CONSUMER_MAPPER, id);
    }

    public List<Consumer> findAll(int limit, int offset) {
        return query("SELECT * FROM consumers ORDER BY id LIMIT ? OFFSET ?", CONSUMER_MAPPER, limit, offset);
    }

    public CheckInResult checkIn(String consumerId, long consumerGroupId, double weight) {
        try (final var connection = ds.getConnection();
             final var statement = connection.prepareCall("{CALL sp_consumers__check_in(?, ?, ?)}")) {
            statement.setString(1, consumerId);
            statement.setLong(2, consumerGroupId);
            statement.setDouble(3, weight);

            // Execute, then process the results
            final var hasResults = statement.execute();
            if (!hasResults) {
                throw new SQLException("Expected statistics result set not returned");
            }

            try (final var rs = statement.getResultSet()) {
                if (!rs.next()) {
                    throw new SQLException("No statistics found");
                }
                // Map the statistics
                final var consumerCheckInResult = CHECK_IN_RESULT_MAPPER.map(rs);
                // Map leased cursors
                if (statement.getMoreResults()) {
                    try (final var leasedCursorsRS = statement.getResultSet()) {
                        while (leasedCursorsRS.next()) {
                            consumerCheckInResult.leasedCursors().add(CURSOR_MAPPER.map(leasedCursorsRS));
                        }
                    }
                }
                return consumerCheckInResult;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error during consumer check-in", e);
        }
    }

    public void delete(String id) {
        update("{CALL sp_consumers__delete(?)}", id);
    }

    public void shutdown(String id) {
        update("{CALL sp_consumers__shutdown(?)}", id);
    }

}
