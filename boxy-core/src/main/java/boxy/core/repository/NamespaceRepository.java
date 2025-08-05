package boxy.core.repository;

import boxy.core.domain.Namespace;
import boxy.core.mapper.IdMapper;
import boxy.core.mapper.NamespaceMapper;
import boxy.core.mapper.RowMapper;

import javax.sql.DataSource;
import java.util.Optional;

public final class NamespaceRepository extends BaseRepository {

    private static final NamespaceMapper NAMESPACE_MAPPER = new NamespaceMapper();
    private static final RowMapper<Long> ID_MAPPER = new IdMapper();

    public NamespaceRepository(final DataSource ds) {
        super(ds);
    }

    public long create(final String path) {
        return queryOne("{CALL sp_namespaces__create(?)}", ID_MAPPER, path).orElseThrow();
    }

    public void delete(final String path) {
        update("{CALL sp_namespaces__delete(?)}", path);
    }

    public void rename(final String path, final String newName) {
        update("{CALL sp_namespaces__rename(?, ?)}", path, newName);
    }

    public void move(final String path, final String newParentPath) {
        update("{CALL sp_namespaces__move(?, ?)}", path, newParentPath);
    }

    public Optional<Namespace> find(final String path) {
        return queryOne("SELECT * FROM namespaces WHERE id = fn_resolve_namespace_id(?)", NAMESPACE_MAPPER, path);
    }
}

