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

    public void commit(final String sessionId, final Map<Long, Long> positions) {
        update("{CALL sp_cursors__commit(?,?)}", sessionId, toJsonObject(positions));
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

    private String toJsonObject(final Map<Long, Long> positions) {
        return positions.entrySet()
                .stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + entry.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

}

