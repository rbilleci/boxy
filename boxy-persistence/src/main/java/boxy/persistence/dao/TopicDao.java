package boxy.persistence.dao;

import boxy.persistence.model.Topic;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;

@RegisterConstructorMapper(Topic.class)
public interface TopicDao extends SqlObject {

    default long create(String tenant, String name, int partitions) {
        return getHandle().createQuery("CALL sp_topics_create(:tenant,:name,:parts)")
                .bind("tenant", tenant)
                .bind("name", name)
                .bind("parts", partitions)
                .mapTo(Long.class)
                .one();
    }

    @SqlQuery("SELECT * FROM topics WHERE id = :id")
    Optional<Topic> find(@Bind("id") long id);

    @SqlQuery("SELECT * FROM topics WHERE tenant = :tenant AND name = :name")
    Optional<Topic> find(@Bind("tenant") String tenant, @Bind("name") String name);

    @SqlUpdate("CALL sp_topics_delete_by_name(:tenant,:name)")
    void delete(@Bind("tenant") String tenant, @Bind("name") String name);

    @SqlUpdate("CALL sp_topics_delete(:topicId)")
    void delete(@Bind("topicId") long topicId);
    
}
