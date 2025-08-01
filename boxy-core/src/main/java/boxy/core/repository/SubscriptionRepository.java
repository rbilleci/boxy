package boxy.core.repository;

import boxy.core.mapper.IdMapper;
import boxy.core.mapper.RowMapper;
import boxy.core.mapper.SubscriptionMapper;
import boxy.core.domain.Subscription;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;

public final class SubscriptionRepository extends BaseRepository {

    private static final SubscriptionMapper SUBSCRIPTION_MAPPER = new SubscriptionMapper();
    private static final RowMapper<Long> ID_MAPPER = new IdMapper();

    public SubscriptionRepository(DataSource ds) {
        super(ds);
    }

    public long create(String tenant, String name) {
        return queryOne("{CALL sp_subscriptions__create(?, ?)}", ID_MAPPER, tenant, name)
                .orElseThrow();
    }

    public void delete(String tenant, String name) {
        update("{CALL sp_subscriptions__delete(?, ?)}", tenant, name);
    }

    public Optional<Subscription> find(String tenant, String name) {
        return queryOne("SELECT * FROM subscriptions WHERE tenant = ? AND name = ?",
                SUBSCRIPTION_MAPPER,
                tenant, name);
    }

    public List<Subscription> findAll(int limit, int offset) {
        return query("SELECT * FROM subscriptions ORDER BY id LIMIT ? OFFSET ?",
                SUBSCRIPTION_MAPPER,
                limit, offset);
    }
}
