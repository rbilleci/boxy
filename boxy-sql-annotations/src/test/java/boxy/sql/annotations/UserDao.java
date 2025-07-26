package boxy.sql.annotations;

import java.util.List;
import java.util.Optional;

/**
 * Example DAO interface for testing SQL annotations.
 */
public interface UserDao {
    
    /**
     * Find a user by ID.
     */
    @SqlQuery("SELECT * FROM users WHERE id = :id")
    User findById(@Bind("id") long id);
    
    /**
     * Find a user by username, returning an Optional.
     */
    @SqlQuery("SELECT * FROM users WHERE username = :username")
    Optional<User> findByUsername(@Bind("username") String username);
    
    /**
     * Find all users.
     */
    @SqlQuery("SELECT * FROM users")
    List<User> findAll();
    
    /**
     * Insert a new user.
     */
    @SqlUpdate("INSERT INTO users(username, email) VALUES (:username, :email)")
    int insert(@Bind("username") String username, @Bind("email") String email);
    
    /**
     * Update a user's email.
     */
    @SqlUpdate("UPDATE users SET email = :email WHERE id = :id")
    int updateEmail(@Bind("id") long id, @Bind("email") String email);
    
    /**
     * Delete a user.
     */
    @SqlUpdate("DELETE FROM users WHERE id = :id")
    int delete(@Bind("id") long id);
    
    /**
     * Insert multiple users in a batch.
     */
    @SqlBatch("INSERT INTO users(username, email) VALUES (:username, :email)")
    void batchInsert(@BindList("username") List<String> usernames, @BindList("email") List<String> emails);
    
    /**
     * Insert a user using bean properties.
     */
    @SqlUpdate("INSERT INTO users(username, email) VALUES (:username, :email)")
    int insertUser(@BindBean User user);
}