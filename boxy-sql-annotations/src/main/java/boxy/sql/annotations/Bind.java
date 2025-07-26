package boxy.sql.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for binding a method parameter to a named SQL parameter.
 * <p>
 * The parameter name in the SQL statement must match the name specified in the annotation.
 * <p>
 * Example:
 * <pre>
 * {@code
 * @SqlQuery("SELECT * FROM users WHERE username = :username")
 * User findByUsername(@Bind("username") String username);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Bind {
    /**
     * The name of the SQL parameter to bind to.
     * @return the parameter name
     */
    String value();
}