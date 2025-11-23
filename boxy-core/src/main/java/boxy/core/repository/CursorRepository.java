package boxy.core.repository;

import boxy.core.mapper.CursorMapper;
import boxy.core.domain.Cursor;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class CursorRepository extends BaseRepository {

    private static final CursorMapper CURSOR_MAPPER = new CursorMapper();

    public CursorRepository(final DataSource ds) {
        super(ds);
    }

    public void commit(final String consumerId, final Map<Long, Long> cursorPositions) {
        update("{CALL sp_cursors__commit(?,?)}", consumerId, toJsonObject(cursorPositions));
    }

    public Optional<Cursor> find(final long id) {
        return queryOne("SELECT * FROM cursors WHERE id = ?", CURSOR_MAPPER, id);
    }

    public Optional<Cursor> find(final long subscriptionId, final long partitionId) {
        return queryOne("SELECT * FROM cursors WHERE subscription_id = ? AND partition_id = ?",
                CURSOR_MAPPER, subscriptionId, partitionId);
    }

    public List<Cursor> findAll(final long subscriptionId) {
        return query("SELECT * FROM cursors WHERE subscription_id = ? ORDER BY id",
                CURSOR_MAPPER, subscriptionId);
    }

    private String toJsonObject(final Map<Long, Long> cursorPositions) {
        return cursorPositions.entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":" + e.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }
}
