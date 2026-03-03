package boxy.core.repository;

import boxy.core.mapper.CursorMapper;
import boxy.core.domain.Cursor;
import boxy.core.repository.BaseRepository;
import boxy.core.util.JsonUtils;
import boxy.core.metrics.BoxyMeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CursorRepository extends BaseRepository {

    private static final Logger log = LoggerFactory.getLogger(CursorRepository.class);
    private static final CursorMapper CURSOR_MAPPER = new CursorMapper();

    public CursorRepository(final DataSource ds) {
        super(ds);
    }

    public void commit(final String consumerId, final Map<Long, Long> cursorPositions) {
        log.debug("Committing cursors: consumerId={} count={}", consumerId, cursorPositions.size());
        Timer.builder(BoxyMeterRegistry.COMMIT_LATENCY)
             .description("Latency of sp_cursors__commit stored procedure calls")
             .register(BoxyMeterRegistry.get())
             .record(() -> update("{CALL sp_cursors__commit(?,?)}", consumerId, toJsonObject(cursorPositions)));
    }

    public Optional<Cursor> find(final long id) {
        log.debug("Finding cursor by id={}", id);
        return queryOne("SELECT * FROM cursors WHERE id = ?", CURSOR_MAPPER, id);
    }

    public Optional<Cursor> find(final long subscriptionId, final long partitionId) {
        log.debug("Finding cursor by subscriptionId={} partitionId={}", subscriptionId, partitionId);
        return queryOne("SELECT * FROM cursors WHERE subscription_id = ? AND partition_id = ?",
                CURSOR_MAPPER, subscriptionId, partitionId);
    }

    public List<Cursor> findAll(final long subscriptionId) {
        log.debug("Finding all cursors for subscriptionId={}", subscriptionId);
        return query("SELECT * FROM cursors WHERE subscription_id = ? ORDER BY id",
                CURSOR_MAPPER, subscriptionId);
    }

    private static String toJsonObject(final Map<Long, Long> cursorPositions) {
        return JsonUtils.jsonObject(cursorPositions);
    }
}
