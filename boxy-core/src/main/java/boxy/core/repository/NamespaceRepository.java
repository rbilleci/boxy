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

    public NamespaceRepository(DataSource ds) {
        super(ds);
    }

    public long create(String path) {
        String parent = null;
        String name = path;
        int idx = path.lastIndexOf('/');
        if (idx >= 0) {
            parent = path.substring(0, idx);
            name = path.substring(idx + 1);
        }
        return queryOne("{CALL sp_namespaces__create(?,?)}", ID_MAPPER, parent, name).orElseThrow();
    }

    public void delete(String path) {
        update("{CALL sp_namespaces__delete(?)}", path);
    }

    public Optional<Namespace> find(String path) {
        return queryOne("SELECT * FROM namespaces WHERE path = ?", NAMESPACE_MAPPER, path);
    }
}

