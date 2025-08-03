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

    public long create(final String path) {
        return queryOne("{CALL sp_namespaces__create(?, '/')}", ID_MAPPER, path).orElseThrow();
    }

    public void delete(String path) {
        update("{CALL sp_namespaces__delete(?)}", path);
    }

    public Optional<Namespace> find(String path) {
        return queryOne("""
                        WITH ns AS (
                            SELECT d.id,
                                   d.parent_id,
                                   d.name,
                                   GROUP_CONCAT(a.name ORDER BY c.depth DESC SEPARATOR '/') AS path,
                                   d.created_at,
                                   d.last_modified_at
                              FROM namespaces d
                              JOIN namespace_closures c ON c.descendant_id = d.id
                              JOIN namespaces a ON a.id = c.ancestor_id
                             GROUP BY d.id
                        )
                        SELECT * FROM ns WHERE path = ?
                        """, NAMESPACE_MAPPER, path);
    }
}

