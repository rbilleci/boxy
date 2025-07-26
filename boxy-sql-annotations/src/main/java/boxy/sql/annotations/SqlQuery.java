package boxy.sql.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that execute SELECT statements.
 * <p>
 * Methods annotated with @SqlQuery can return:
 * <ul>
 *   <li>A mapped object (e.g., User)</li>
 *   <li>A primitive or boxed value</li>
 *   <li>List&lt;T&gt;, Set&lt;T&gt; for multiple results</li>
 *   <li>Stream&lt;T&gt; for lazy iteration</li>
 *   <li>Optional&lt;T&gt; for optional single result</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * {@code
 * @SqlQuery("SELECT * FROM users WHERE id = :id")
 * User findById(@Bind("id") long id);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SqlQuery {
    /**
     * SQL query to execute.
     * @return the SQL query string
     */
    String value();
}