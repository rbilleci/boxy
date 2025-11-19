package boxy.core.repository;

import boxy.core.mapper.ConsumerMapper;
import boxy.core.domain.Consumer;
import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

public final class ConsumerRepository extends BaseRepository {

    private static final ConsumerMapper CONSUMER_MAPPER = new ConsumerMapper();

    public ConsumerRepository(final DataSource ds) {
        super(ds);
    }

    public Optional<Consumer> find(final String id) {
        return queryOne("SELECT * FROM consumers WHERE id = ?", CONSUMER_MAPPER, id);
    }

    public List<Consumer> findAll(final int limit, final int offset) {
        return query("SELECT * FROM consumers ORDER BY id LIMIT ? OFFSET ?", CONSUMER_MAPPER, limit, offset);
    }

    public void register(final String consumerId, final String subscriptionName, final List<String> topicPaths) {
        update("{CALL sp_consumers__register(?, ?, ?)}", consumerId, subscriptionName, toJsonArray(topicPaths));
    }

    public void deregister(final String id) {
        update("{CALL sp_consumers__deregister(?)}", id);
    }

    private String toJsonArray(final List<String> values) {
        return values.stream()
                .map(value -> value
                        .replace("\\", "\\\\")
                        .replace("\"", "\\\""))
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

}
