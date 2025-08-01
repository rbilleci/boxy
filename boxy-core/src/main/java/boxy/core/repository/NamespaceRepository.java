package boxy.core.repository;

import boxy.core.domain.Namespace;
import boxy.core.mapper.NamespaceMapper;

import javax.sql.DataSource;
import java.util.Optional;

public final class NamespaceRepository extends BaseRepository {

    private static final NamespaceMapper NAMESPACE_MAPPER = new NamespaceMapper();

    public NamespaceRepository(DataSource ds) {
        super(ds);
    }

    public long create(String tenant, String name) {
        return queryOne("{CALL sp_namespaces__create(?, ?)}", rs -> rs.getLong(1), tenant, name).orElseThrow();
    }

    public void delete(String tenant, String name) {
        update("{CALL sp_namespaces__delete(?, ?)}", tenant, name);
    }

    public Optional<Namespace> find(String tenant, String name) {
        return queryOne("SELECT * FROM namespaces WHERE tenant = ? AND name = ?", NAMESPACE_MAPPER, tenant, name);
    }
}

