package boxy.sql.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for binding a collection parameter to a SQL IN clause or for batch operations.
 * <p>
 * When used with a collection parameter, each element in the collection is bound
 * to a separate parameter in the SQL statement.
 * <p>
 * Example for IN clause:
 * <pre>
 * {@code
 * @SqlQuery("SELECT * FROM users WHERE id IN (<ids>)")
 * List<User> findByIds(@BindList("ids") List<Long> ids);
 * }
 * </pre>
 * <p>
 * Example for batch operations:
 * <pre>
 * {@code
 * @SqlBatch("INSERT INTO logs(message) VALUES (:message)")
 * void insertLogs(@BindList("message") List<String> messages);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface BindList {
    /**
     * The name of the SQL parameter to bind the list to.
     * @return the parameter name
     */
    String value();
    
    /**
     * The delimiter to use between list elements in the SQL.
     * Default is a comma followed by a space.
     * 
     * @return the delimiter string
     */
    String delimiter() default ", ";
}