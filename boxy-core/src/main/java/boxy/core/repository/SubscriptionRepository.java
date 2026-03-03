package boxy.core.repository;

import boxy.core.mapper.IdMapper;
import boxy.core.mapper.RowMapper;
import boxy.core.mapper.SubscriptionMapper;
import boxy.core.domain.Subscription;
import boxy.core.repository.BaseRepository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionRepository extends BaseRepository {

    private static final SubscriptionMapper SUBSCRIPTION_MAPPER = new SubscriptionMapper();
    private static final RowMapper<Long> ID_MAPPER = new IdMapper();

    public SubscriptionRepository(final DataSource ds) {
        super(ds);
    }

    public long create(final String name) {
        return queryOne("{CALL sp_subscriptions__create(?)}", ID_MAPPER, name).orElseThrow();
    }

    public void delete(final String name) {
        update("{CALL sp_subscriptions__delete(?)}", name);
    }

    public long subscribe(final String subscription, final String path, final String topic) {
        return queryOne("{CALL sp_subscriptions__subscribe(?,?,?)}", ID_MAPPER, subscription, path, topic).orElseThrow();
    }

    public void unsubscribe(final String subscription, final String path, final String topic) {
        update("{CALL sp_subscriptions__unsubscribe(?,?,?)}", subscription, path, topic);
    }

    public Optional<Subscription> find(final String name) {
        return queryOne("SELECT * FROM subscriptions WHERE name = ?", SUBSCRIPTION_MAPPER, name);
    }

    public List<Subscription> findAll(final int limit, final int offset) {
        return query("SELECT * FROM subscriptions ORDER BY id LIMIT ? OFFSET ?", SUBSCRIPTION_MAPPER, limit, offset);
    }
}
