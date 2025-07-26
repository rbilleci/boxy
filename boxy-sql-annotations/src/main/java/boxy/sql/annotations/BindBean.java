package boxy.sql.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for binding properties of a Java object to SQL parameters.
 * <p>
 * When a parameter is annotated with @BindBean, the properties of the object
 * are bound to SQL parameters with matching names. For example, if a User object
 * has properties 'id', 'name', and 'email', those properties can be referenced
 * in the SQL as ':id', ':name', and ':email'.
 * <p>
 * Example:
 * <pre>
 * {@code
 * @SqlUpdate("INSERT INTO users(id, name, email) VALUES (:id, :name, :email)")
 * int insertUser(@BindBean User user);
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface BindBean {
    /**
     * Optional prefix to apply to all property names when binding.
     * <p>
     * If specified, property names will be prefixed with this value plus a dot.
     * For example, if prefix is "user", then properties will be bound as
     * ":user.id", ":user.name", etc.
     * 
     * @return the prefix to apply to property names (default is empty string)
     */
    String value() default "";
}