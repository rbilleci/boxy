package boxy.sql.annotations;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Processor for SQL annotations that creates dynamic proxies for interfaces with SQL annotations.
 * <p>
 * This class is the main entry point for using the SQL annotations. It creates proxies for
 * interfaces that have methods annotated with {@link SqlQuery}, {@link SqlUpdate}, or {@link SqlBatch}.
 * <p>
 * Example usage:
 * <pre>
 * {@code
 * // Define an interface with SQL annotations
 * public interface UserDao {
 *     @SqlQuery("SELECT * FROM users WHERE id = :id")
 *     User findById(@Bind("id") long id);
 *     
 *     @SqlUpdate("INSERT INTO users(name, email) VALUES (:name, :email)")
 *     int insert(@Bind("name") String name, @Bind("email") String email);
 * }
 * 
 * // Create a proxy for the interface
 * DataSource dataSource = ...;
 * UserDao userDao = SqlAnnotationProcessor.create(UserDao.class, dataSource);
 * 
 * // Use the proxy
 * User user = userDao.findById(123);
 * int rowsAffected = userDao.insert("John", "john@example.com");
 * }
 * </pre>
 */
public class SqlAnnotationProcessor {
    
    private static final Map<Class<?>, Object> PROXIES = new ConcurrentHashMap<>();
    
    /**
     * Creates a proxy for the given interface that implements the SQL annotations.
     *
     * @param interfaceClass the interface class to create a proxy for
     * @param connection a function that provides a JDBC connection
     * @param <T> the interface type
     * @return a proxy instance of the interface
     * @throws IllegalArgumentException if the class is not an interface or has methods that cannot be implemented
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> interfaceClass, Function<Void, Connection> connection) {
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException("Class must be an interface: " + interfaceClass.getName());
        }
        
        return (T) PROXIES.computeIfAbsent(interfaceClass, 
            clazz -> Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[] { interfaceClass },
                new SqlInvocationHandler(connection)
            )
        );
    }
    
    /**
     * Creates a proxy for the given interface that implements the SQL annotations.
     *
     * @param interfaceClass the interface class to create a proxy for
     * @param dataSource the JDBC data source to use for connections
     * @param <T> the interface type
     * @return a proxy instance of the interface
     * @throws IllegalArgumentException if the class is not an interface or has methods that cannot be implemented
     */
    public static <T> T create(Class<T> interfaceClass, javax.sql.DataSource dataSource) {
        return create(interfaceClass, unused -> {
            try {
                return dataSource.getConnection();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get connection from data source", e);
            }
        });
    }
    
    /**
     * Invocation handler that processes SQL annotations and executes SQL statements.
     */
    private static class SqlInvocationHandler implements InvocationHandler {
        private final Function<Void, Connection> connectionProvider;
        private final Map<Method, SqlMethod> methodCache = new ConcurrentHashMap<>();
        
        public SqlInvocationHandler(Function<Void, Connection> connectionProvider) {
            this.connectionProvider = connectionProvider;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            // Handle Object methods like toString(), equals(), hashCode()
            if (method.getDeclaringClass() == Object.class) {
                return handleObjectMethod(proxy, method, args);
            }
            
            // Get or create the SQL method handler
            SqlMethod sqlMethod = methodCache.computeIfAbsent(method, this::createSqlMethod);
            
            // Execute the SQL method
            try (Connection conn = connectionProvider.apply(null)) {
                return sqlMethod.execute(conn, args);
            } catch (SQLException e) {
                throw new RuntimeException("SQL execution error", e);
            }
        }
        
        private Object handleObjectMethod(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            if ("toString".equals(methodName)) {
                return proxy.getClass().getInterfaces()[0].getName() + "$Proxy";
            } else if ("equals".equals(methodName)) {
                return proxy == args[0];
            } else if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            throw new UnsupportedOperationException("Unexpected Object method: " + method);
        }
        
        private SqlMethod createSqlMethod(Method method) {
            if (method.isAnnotationPresent(SqlQuery.class)) {
                return new SqlQueryMethod(method);
            } else if (method.isAnnotationPresent(SqlUpdate.class)) {
                return new SqlUpdateMethod(method);
            } else if (method.isAnnotationPresent(SqlBatch.class)) {
                return new SqlBatchMethod(method);
            }
            throw new UnsupportedOperationException("Method not annotated with SQL annotation: " + method);
        }
    }
    
    /**
     * Interface for SQL method handlers.
     */
    private interface SqlMethod {
        Object execute(Connection connection, Object[] args) throws SQLException;
    }
    
    /**
     * Handler for methods annotated with {@link SqlQuery}.
     */
    private static class SqlQueryMethod implements SqlMethod {
        private final Method method;
        private final String sql;
        private final List<ParameterBinding> parameterBindings;
        private final ResultMapper<?> resultMapper;
        
        public SqlQueryMethod(Method method) {
            this.method = method;
            this.sql = method.getAnnotation(SqlQuery.class).value();
            this.parameterBindings = createParameterBindings(method);
            this.resultMapper = createResultMapper(method.getReturnType(), method.getGenericReturnType());
        }
        
        @Override
        public Object execute(Connection connection, Object[] args) throws SQLException {
            String preparedSql = sql; // In a real implementation, we would process named parameters
            
            try (PreparedStatement stmt = connection.prepareStatement(preparedSql)) {
                bindParameters(stmt, args);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return resultMapper.map(rs);
                }
            }
        }
        
        private void bindParameters(PreparedStatement stmt, Object[] args) throws SQLException {
            // In a real implementation, we would bind parameters based on the parameter bindings
            // For now, this is a placeholder
            for (int i = 0; i < parameterBindings.size(); i++) {
                if (args[i] != null) {
                    stmt.setObject(i + 1, args[i]);
                } else {
                    stmt.setNull(i + 1, Types.NULL);
                }
            }
        }
    }
    
    /**
     * Handler for methods annotated with {@link SqlUpdate}.
     */
    private static class SqlUpdateMethod implements SqlMethod {
        private final Method method;
        private final String sql;
        private final List<ParameterBinding> parameterBindings;
        private final boolean returnsRowCount;
        
        public SqlUpdateMethod(Method method) {
            this.method = method;
            this.sql = method.getAnnotation(SqlUpdate.class).value();
            this.parameterBindings = createParameterBindings(method);
            this.returnsRowCount = method.getReturnType() == int.class || method.getReturnType() == Integer.class;
        }
        
        @Override
        public Object execute(Connection connection, Object[] args) throws SQLException {
            String preparedSql = sql; // In a real implementation, we would process named parameters
            
            try (PreparedStatement stmt = connection.prepareStatement(preparedSql)) {
                bindParameters(stmt, args);
                
                int rowCount = stmt.executeUpdate();
                return returnsRowCount ? rowCount : null;
            }
        }
        
        private void bindParameters(PreparedStatement stmt, Object[] args) throws SQLException {
            // In a real implementation, we would bind parameters based on the parameter bindings
            // For now, this is a placeholder
            for (int i = 0; i < parameterBindings.size(); i++) {
                if (args[i] != null) {
                    stmt.setObject(i + 1, args[i]);
                } else {
                    stmt.setNull(i + 1, Types.NULL);
                }
            }
        }
    }
    
    /**
     * Handler for methods annotated with {@link SqlBatch}.
     */
    private static class SqlBatchMethod implements SqlMethod {
        private final Method method;
        private final String sql;
        private final List<ParameterBinding> parameterBindings;
        private final boolean returnsRowCounts;
        
        public SqlBatchMethod(Method method) {
            this.method = method;
            this.sql = method.getAnnotation(SqlBatch.class).value();
            this.parameterBindings = createParameterBindings(method);
            this.returnsRowCounts = method.getReturnType() == int[].class;
        }
        
        @Override
        public Object execute(Connection connection, Object[] args) throws SQLException {
            String preparedSql = sql; // In a real implementation, we would process named parameters
            
            // Determine batch size from the first collection parameter
            int batchSize = 0;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Collection) {
                    batchSize = ((Collection<?>) args[i]).size();
                    break;
                }
            }
            
            if (batchSize == 0) {
                throw new IllegalArgumentException("No collection parameters found for batch operation");
            }
            
            try (PreparedStatement stmt = connection.prepareStatement(preparedSql)) {
                // In a real implementation, we would bind batch parameters
                // For now, this is a placeholder
                for (int i = 0; i < batchSize; i++) {
                    // Bind parameters for this batch
                    stmt.addBatch();
                }
                
                int[] rowCounts = stmt.executeBatch();
                return returnsRowCounts ? rowCounts : null;
            }
        }
    }
    
    /**
     * Interface for mapping SQL result sets to Java objects.
     */
    private interface ResultMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }
    
    /**
     * Creates parameter bindings for a method.
     */
    private static List<ParameterBinding> createParameterBindings(Method method) {
        Parameter[] parameters = method.getParameters();
        List<ParameterBinding> bindings = new ArrayList<>(parameters.length);
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            
            if (param.isAnnotationPresent(Bind.class)) {
                String name = param.getAnnotation(Bind.class).value();
                bindings.add(new BindParameterBinding(i, name));
            } else if (param.isAnnotationPresent(BindBean.class)) {
                String prefix = param.getAnnotation(BindBean.class).value();
                bindings.add(new BindBeanParameterBinding(i, prefix));
            } else if (param.isAnnotationPresent(BindList.class)) {
                String name = param.getAnnotation(BindList.class).value();
                String delimiter = param.getAnnotation(BindList.class).delimiter();
                bindings.add(new BindListParameterBinding(i, name, delimiter));
            } else {
                // Default to position-based binding if no annotation is present
                bindings.add(new BindParameterBinding(i, "p" + i));
            }
        }
        
        return bindings;
    }
    
    /**
     * Creates a result mapper for a return type.
     */
    @SuppressWarnings("unchecked")
    private static <T> ResultMapper<T> createResultMapper(Class<?> returnType, Type genericReturnType) {
        if (returnType == void.class || returnType == Void.class) {
            return rs -> null;
        } else if (returnType == List.class) {
            return (ResultMapper<T>) new ListResultMapper<>(getGenericType(genericReturnType));
        } else if (returnType == Set.class) {
            return (ResultMapper<T>) new SetResultMapper<>(getGenericType(genericReturnType));
        } else if (returnType == Stream.class) {
            return (ResultMapper<T>) new StreamResultMapper<>(getGenericType(genericReturnType));
        } else if (returnType == Optional.class) {
            return (ResultMapper<T>) new OptionalResultMapper<>(getGenericType(genericReturnType));
        } else if (isPrimitiveOrBoxed(returnType)) {
            return (ResultMapper<T>) new ScalarResultMapper(returnType);
        } else {
            return (ResultMapper<T>) new SingleResultMapper<>(returnType);
        }
    }
    
    private static Class<?> getGenericType(Type genericType) {
        if (genericType instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length > 0 && typeArguments[0] instanceof Class) {
                return (Class<?>) typeArguments[0];
            }
        }
        return Object.class;
    }
    
    private static boolean isPrimitiveOrBoxed(Class<?> type) {
        return type.isPrimitive() || 
               type == Boolean.class || 
               type == Byte.class || 
               type == Character.class || 
               type == Short.class || 
               type == Integer.class || 
               type == Long.class || 
               type == Float.class || 
               type == Double.class ||
               type == String.class;
    }
    
    /**
     * Base class for parameter bindings.
     */
    private static abstract class ParameterBinding {
        protected final int index;
        
        protected ParameterBinding(int index) {
            this.index = index;
        }
        
        public int getIndex() {
            return index;
        }
        
        public abstract void bind(PreparedStatement stmt, Object[] args) throws SQLException;
    }
    
    /**
     * Binding for parameters annotated with {@link Bind}.
     */
    private static class BindParameterBinding extends ParameterBinding {
        private final String name;
        
        public BindParameterBinding(int index, String name) {
            super(index);
            this.name = name;
        }
        
        @Override
        public void bind(PreparedStatement stmt, Object[] args) throws SQLException {
            if (args[index] != null) {
                stmt.setObject(index + 1, args[index]);
            } else {
                stmt.setNull(index + 1, Types.NULL);
            }
        }
    }
    
    /**
     * Binding for parameters annotated with {@link BindBean}.
     */
    private static class BindBeanParameterBinding extends ParameterBinding {
        private final String prefix;
        
        public BindBeanParameterBinding(int index, String prefix) {
            super(index);
            this.prefix = prefix;
        }
        
        @Override
        public void bind(PreparedStatement stmt, Object[] args) throws SQLException {
            // In a real implementation, we would use reflection to get bean properties
            // and bind them to named parameters
        }
    }
    
    /**
     * Binding for parameters annotated with {@link BindList}.
     */
    private static class BindListParameterBinding extends ParameterBinding {
        private final String name;
        private final String delimiter;
        
        public BindListParameterBinding(int index, String name, String delimiter) {
            super(index);
            this.name = name;
            this.delimiter = delimiter;
        }
        
        @Override
        public void bind(PreparedStatement stmt, Object[] args) throws SQLException {
            // In a real implementation, we would bind each element in the collection
            // to a separate parameter
        }
    }
    
    /**
     * Mapper for single object results.
     */
    private static class SingleResultMapper<T> implements ResultMapper<T> {
        private final Class<T> type;
        
        public SingleResultMapper(Class<T> type) {
            this.type = type;
        }
        
        @Override
        public T map(ResultSet rs) throws SQLException {
            if (rs.next()) {
                return mapRow(rs);
            }
            return null;
        }
        
        protected T mapRow(ResultSet rs) throws SQLException {
            // In a real implementation, we would use reflection to create and populate the object
            // For now, this is a placeholder
            try {
                T instance = type.getDeclaredConstructor().newInstance();
                // Populate instance from result set
                return instance;
            } catch (ReflectiveOperationException e) {
                throw new SQLException("Failed to create instance of " + type, e);
            }
        }
    }
    
    /**
     * Mapper for list results.
     */
    private static class ListResultMapper<T> implements ResultMapper<List<T>> {
        private final SingleResultMapper<T> rowMapper;
        
        public ListResultMapper(Class<T> elementType) {
            this.rowMapper = new SingleResultMapper<>(elementType);
        }
        
        @Override
        public List<T> map(ResultSet rs) throws SQLException {
            List<T> results = new ArrayList<>();
            while (rs.next()) {
                results.add(rowMapper.mapRow(rs));
            }
            return results;
        }
    }
    
    /**
     * Mapper for set results.
     */
    private static class SetResultMapper<T> implements ResultMapper<Set<T>> {
        private final SingleResultMapper<T> rowMapper;
        
        public SetResultMapper(Class<T> elementType) {
            this.rowMapper = new SingleResultMapper<>(elementType);
        }
        
        @Override
        public Set<T> map(ResultSet rs) throws SQLException {
            Set<T> results = new HashSet<>();
            while (rs.next()) {
                results.add(rowMapper.mapRow(rs));
            }
            return results;
        }
    }
    
    /**
     * Mapper for stream results.
     */
    private static class StreamResultMapper<T> implements ResultMapper<Stream<T>> {
        private final SingleResultMapper<T> rowMapper;
        
        public StreamResultMapper(Class<T> elementType) {
            this.rowMapper = new SingleResultMapper<>(elementType);
        }
        
        @Override
        public Stream<T> map(ResultSet rs) throws SQLException {
            List<T> results = new ArrayList<>();
            while (rs.next()) {
                results.add(rowMapper.mapRow(rs));
            }
            return results.stream();
        }
    }
    
    /**
     * Mapper for optional results.
     */
    private static class OptionalResultMapper<T> implements ResultMapper<Optional<T>> {
        private final SingleResultMapper<T> rowMapper;
        
        public OptionalResultMapper(Class<T> elementType) {
            this.rowMapper = new SingleResultMapper<>(elementType);
        }
        
        @Override
        public Optional<T> map(ResultSet rs) throws SQLException {
            if (rs.next()) {
                return Optional.ofNullable(rowMapper.mapRow(rs));
            }
            return Optional.empty();
        }
    }
    
    /**
     * Mapper for scalar results (primitives, boxed primitives, and strings).
     */
    private static class ScalarResultMapper implements ResultMapper<Object> {
        private final Class<?> type;
        
        public ScalarResultMapper(Class<?> type) {
            this.type = type;
        }
        
        @Override
        public Object map(ResultSet rs) throws SQLException {
            if (rs.next()) {
                if (type == boolean.class || type == Boolean.class) {
                    return rs.getBoolean(1);
                } else if (type == byte.class || type == Byte.class) {
                    return rs.getByte(1);
                } else if (type == short.class || type == Short.class) {
                    return rs.getShort(1);
                } else if (type == int.class || type == Integer.class) {
                    return rs.getInt(1);
                } else if (type == long.class || type == Long.class) {
                    return rs.getLong(1);
                } else if (type == float.class || type == Float.class) {
                    return rs.getFloat(1);
                } else if (type == double.class || type == Double.class) {
                    return rs.getDouble(1);
                } else if (type == String.class) {
                    return rs.getString(1);
                } else {
                    return rs.getObject(1);
                }
            }
            
            // Return default values for primitives, null for objects
            if (type == boolean.class) {
                return false;
            } else if (type == byte.class) {
                return (byte) 0;
            } else if (type == short.class) {
                return (short) 0;
            } else if (type == int.class) {
                return 0;
            } else if (type == long.class) {
                return 0L;
            } else if (type == float.class) {
                return 0.0f;
            } else if (type == double.class) {
                return 0.0d;
            } else {
                return null;
            }
        }
    }
}