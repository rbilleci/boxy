package boxy.core.repository;

import boxy.core.domain.ConsumerRegistration;
import boxy.core.mapper.ConsumerRegistrationMapper;

import javax.sql.DataSource;
import java.util.List;

public final class ConsumerRegistrationRepository extends BaseRepository {

    private static final ConsumerRegistrationMapper CONSUMER_REGISTRATION_MAPPER = new ConsumerRegistrationMapper();

    public ConsumerRegistrationRepository(final DataSource ds) {
        super(ds);
    }

    public List<ConsumerRegistration> findAll(final String consumerId) {
        return query("SELECT * FROM consumer_registrations WHERE consumer_id = ? ORDER BY topic_id",
                CONSUMER_REGISTRATION_MAPPER, consumerId);
    }
}
