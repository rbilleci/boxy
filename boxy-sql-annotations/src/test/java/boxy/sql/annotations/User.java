package boxy.sql.annotations;

/**
 * Simple User class for testing SQL annotations.
 */
public class User {
    private long id;
    private String username;
    private String email;
    
    /**
     * Default constructor required for reflection-based instantiation.
     */
    public User() {
    }
    
    /**
     * Constructor with all fields.
     */
    public User(long id, String username, String email) {
        this.id = id;
        this.username = username;
        this.email = email;
    }
    
    /**
     * Get the user ID.
     */
    public long getId() {
        return id;
    }
    
    /**
     * Set the user ID.
     */
    public void setId(long id) {
        this.id = id;
    }
    
    /**
     * Get the username.
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Set the username.
     */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /**
     * Get the email.
     */
    public String getEmail() {
        return email;
    }
    
    /**
     * Set the email.
     */
    public void setEmail(String email) {
        this.email = email;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}