package boxy.mysql.worker;

import java.util.List;

/**
 * Functional interface for processing events polled from Boxy.
 *
 * Implementations should:
 * - Handle the event processing logic (transform, filter, route, etc.)
 * - Not block for extended periods (use thread pools if needed)
 * - Return true to acknowledge the batch (cursor will be committed)
 * - Return false or throw to skip the batch (cursor not advanced; retry on next poll)
 *
 * @param <T> Event payload type (e.g., String, byte[], custom record)
 * @since 1.0
 */
@FunctionalInterface
public interface EventHandler<T> {
    /**
     * Processes a batch of events.
     *
     * @param events The polled events (non-empty list)
     * @return true to commit the batch; false to skip/retry
     * @throws Exception If processing fails; Worker will retry the batch
     */
    boolean handle(List<PolledEvent<T>> events) throws Exception;
}
