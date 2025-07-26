package boxy.sql.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that execute INSERT, UPDATE, or DELETE statements.
 * <p>
 * Methods annotated with @SqlUpdate can return:
 * <ul>
 *   <li>void - no return value</li>
 *   <li>int - number of rows affected</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * {@code
 * @SqlUpdate("UPDATE users SET name = :name WHERE id = :id")
 * int updateName(@Bind("id") long id, @Bind("name") String name);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SqlUpdate {
    /**
     * SQL update statement to execute.
     * @return the SQL update string
     */
    String value();
}