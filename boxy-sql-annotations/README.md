# Boxy SQL Annotations

A lightweight, zero-dependency library for declarative SQL operations using Java interface annotations.

## Overview

Boxy SQL Annotations allows you to define SQL operations using annotations on Java interface methods. This approach eliminates the need for boilerplate JDBC code and provides a clean, declarative way to interact with your database.

## Features

- **Declarative SQL**: Write SQL directly in annotations
- **Interface-based**: No implementation code needed
- **Type-safe**: Return types are automatically mapped
- **Lightweight**: Zero dependencies
- **High performance**: Minimal overhead

## Usage

### 1. Define an interface with SQL annotations

```java
public interface UserDao {
    @SqlQuery("SELECT * FROM users WHERE id = :id")
    User findById(@Bind("id") long id);
    
    @SqlQuery("SELECT * FROM users WHERE username = :username")
    Optional<User> findByUsername(@Bind("username") String username);
    
    @SqlQuery("SELECT * FROM users")
    List<User> findAll();
    
    @SqlUpdate("INSERT INTO users(username, email) VALUES (:username, :email)")
    int insert(@Bind("username") String username, @Bind("email") String email);
    
    @SqlUpdate("UPDATE users SET email = :email WHERE id = :id")
    int updateEmail(@Bind("id") long id, @Bind("email") String email);
    
    @SqlBatch("INSERT INTO users(username, email) VALUES (:username, :email)")
    void batchInsert(@BindList("username") List<String> usernames, @BindList("email") List<String> emails);
}
```

### 2. Create a proxy for the interface

```java
// Using a DataSource
DataSource dataSource = ...;
UserDao userDao = SqlAnnotationProcessor.create(UserDao.class, dataSource);

// Or using a connection provider function
Function<Void, Connection> connectionProvider = unused -> {
    // Return a JDBC connection
    return connection;
};
UserDao userDao = SqlAnnotationProcessor.create(UserDao.class, connectionProvider);
```

### 3. Use the proxy

```java
// Query for a single user
User user = userDao.findById(123);

// Query with optional result
Optional<User> optionalUser = userDao.findByUsername("johndoe");

// Query for multiple users
List<User> allUsers = userDao.findAll();

// Insert a new user
int rowsAffected = userDao.insert("johndoe", "john@example.com");

// Update a user
userDao.updateEmail(123, "newemail@example.com");

// Batch insert
List<String> usernames = List.of("user1", "user2", "user3");
List<String> emails = List.of("user1@example.com", "user2@example.com", "user3@example.com");
userDao.batchInsert(usernames, emails);
```

## Annotations

### SQL Operation Annotations

- **@SqlQuery**: Executes a SELECT statement
  - Valid return types: Object, primitive, List<T>, Set<T>, Stream<T>, Optional<T>
  
- **@SqlUpdate**: Executes INSERT, UPDATE, or DELETE
  - Valid return types: void, int (rows affected)
  
- **@SqlBatch**: Performs batch operations
  - Valid return types: void, int[] (rows affected per batch item)

### Parameter Binding Annotations

- **@Bind("name")**: Binds a parameter value to a named SQL parameter
  
- **@BindBean**: Binds properties of a Java object to SQL parameters
  
- **@BindList("name")**: Binds a collection for IN clauses or batch operations

## Implementation Details

The library uses Java's dynamic proxy mechanism to create implementations of your interfaces at runtime. When a method is called on the proxy:

1. SQL is prepared with named placeholders
2. Method arguments are bound to SQL parameters
3. The statement is executed
4. Results are mapped to the declared return type

All of this happens with minimal overhead and no external dependencies.