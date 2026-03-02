package boxy.mysql.repository;

import boxy.core.mapper.ConsumerMapper;
import boxy.core.domain.Consumer;
import boxy.core.repository.BaseRepository;
import boxy.core.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class ConsumerRepository extends BaseRepository {

    private static final Logger log = LoggerFactory.getLogger(ConsumerRepository.class);
    private static final ConsumerMapper CONSUMER_MAPPER = new ConsumerMapper();

    public ConsumerRepository(final DataSource ds) {
        super(ds);
    }

    public Optional<Consumer> find(final String id) {
        log.debug("Finding consumer by id={}", id);
        return queryOne("SELECT * FROM consumers WHERE id = ?", CONSUMER_MAPPER, id);
    }

    public List<Consumer> findAll(final int limit, final int offset) {
        log.debug("Finding all consumers: limit={} offset={}", limit, offset);
        return query("SELECT * FROM consumers ORDER BY id LIMIT ? OFFSET ?", CONSUMER_MAPPER, limit, offset);
    }

    public void register(final String consumerId, final String subscriptionName, final List<String> topicPaths) {
        log.debug("Registering consumer: consumerId={} subscriptionName={} topicCount={}", consumerId, subscriptionName, topicPaths.size());
        update("{CALL sp_consumers__register(?, ?, ?)}", consumerId, subscriptionName, toJsonArray(topicPaths));
    }

    public void deregister(final String id) {
        log.debug("Deregistering consumer: id={}", id);
        update("{CALL sp_consumers__deregister(?)}", id);
    }

    private static String toJsonArray(final List<String> values) {
        return JsonUtils.jsonArray(values);
    }

}
