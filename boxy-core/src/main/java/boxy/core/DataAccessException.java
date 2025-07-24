package boxy.core;

import java.sql.SQLException;

public class DataAccessException extends RuntimeException {

    public DataAccessException(SQLException e) {
        super(e.getMessage(), e);
    }
}