package boxy.sql.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that perform batch INSERT, UPDATE, or DELETE operations.
 * <p>
 * Methods annotated with @SqlBatch can return:
 * <ul>
 *   <li>void - no return value</li>
 *   <li>int[] - number of rows affected per batch item</li>
 * </ul>
 * <p>
 * Example:
 * <pre>
 * {@code
 * @SqlBatch("INSERT INTO logs(message, timestamp) VALUES (:message, :timestamp)")
 * void insertLogs(@Bind("message") List<String> messages, @Bind("timestamp") List<Instant> timestamps);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SqlBatch {
    /**
     * SQL statement to execute in batch.
     * @return the SQL statement string
     */
    String value();
}