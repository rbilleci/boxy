package boxy.core.repository;

import boxy.core.mapper.CursorMapper;
import boxy.core.domain.Cursor;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class CursorRepository extends BaseRepository {

    private static final CursorMapper CURSOR_MAPPER = new CursorMapper();

    public CursorRepository(DataSource ds) {
        super(ds);
    }

    public void commit(long id, long position) {
        update("{CALL sp_cursors__commit(?,?)}", id, position);
    }

    public Optional<Cursor> find(long id) {
        return queryOne("SELECT * FROM cursors WHERE id = ?", CURSOR_MAPPER, id);
    }

    public Optional<Cursor> find(long consumerGroupId, long partitionId) {
        return queryOne("""
                        SELECT c.* FROM cursors c
                        JOIN subscriptions st ON c.subscription_id = st.id
                        WHERE st.consumer_group_id = ? AND c.partition_id = ?
                        """, CURSOR_MAPPER, consumerGroupId, partitionId);
    }

    public List<Cursor> findAll(long consumerGroupId) {
        return query("""
                        SELECT c.* FROM cursors c
                        JOIN subscriptions st ON c.subscription_id = st.id
                        WHERE st.consumer_group_id = ? ORDER BY c.id
                        """, CURSOR_MAPPER, consumerGroupId);
    }


    public List<Cursor> findLeasable(int limit, int offset) {
        return query(
                "SELECT * FROM unleased_cursors_view ORDER BY id LIMIT ? OFFSET ?",
                CURSOR_MAPPER,
                limit, offset);
    }

    public List<Cursor> findLeasable(long consumerGroupId, int limit, int offset) {
        return query(
                "SELECT * FROM unleased_cursors_view WHERE consumer_group_id = ? ORDER BY id LIMIT ? OFFSET ?",
                CURSOR_MAPPER,
                consumerGroupId, limit, offset);
    }

}
