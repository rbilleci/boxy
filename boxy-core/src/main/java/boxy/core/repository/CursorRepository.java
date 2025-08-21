package boxy.core.repository;

import boxy.core.mapper.CursorMapper;
import boxy.core.domain.Cursor;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class CursorRepository extends BaseRepository {

    private static final CursorMapper CURSOR_MAPPER = new CursorMapper();

    public CursorRepository(final DataSource ds) {
        super(ds);
    }

    public void commit(final long id, final long position) {
        update("{CALL sp_cursors__commit(?,?)}", id, position);
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

}
