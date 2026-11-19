package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Manages JDBC driver loading and MySQL/MariaDB connections.
 */
public final class DatabaseConnection {

    private static final String DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    static {
        try {
            Class.forName(DRIVER_CLASS);
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(
                    "MySQL JDBC Driver not found on classpath: " + DRIVER_CLASS);
        }
    }

    private DatabaseConnection() {
    }

    public static final class ConnectionStatus {
        private final boolean connected;
        private final String message;
        private final String serverVersion;

        public ConnectionStatus(boolean connected, String message, String serverVersion) {
            this.connected = connected;
            this.message = message;
            this.serverVersion = serverVersion;
        }

        public boolean isConnected() {
            return connected;
        }

        public String getMessage() {
            return message;
        }

        public String getServerVersion() {
            return serverVersion;
        }
    }

    public static Connection getServerConnection() throws SQLException {
        DriverManager.setLoginTimeout(5);
        return DriverManager.getConnection(
                DatabaseConfig.getServerUrl(),
                DatabaseConfig.getUsername(),
                DatabaseConfig.getPassword()
        );
    }

    public static Connection getConnection() throws SQLException {
        DriverManager.setLoginTimeout(5);
        return DriverManager.getConnection(
                DatabaseConfig.getDatabaseUrl(),
                DatabaseConfig.getUsername(),
                DatabaseConfig.getPassword()
        );
    }

    public static ConnectionStatus testConnection() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT VERSION()");
             ResultSet rs = ps.executeQuery()) {
            String ver = rs.next() ? rs.getString(1) : "MySQL";
            return new ConnectionStatus(true, "Connected (" + DatabaseConfig.getDatabaseName() + ")", ver);
        } catch (SQLException e) {
            return new ConnectionStatus(false, formatSqlError(e), "");
        }
    }

    public static String formatSqlError(SQLException e) {
        String msg = e.getMessage() == null ? "Unknown SQL error" : e.getMessage();
        if (msg.contains("Communications link failure") || msg.contains("Connection refused")) {
            return "Cannot reach MySQL on " + DatabaseConfig.getHost() + ":" + DatabaseConfig.getPort()
                    + ". Start MySQL in XAMPP Control Panel.";
        }
        if (msg.contains("Access denied")) {
            return "Access denied for MySQL user '" + DatabaseConfig.getUsername()
                    + "'. Check config/db.properties.";
        }
        return msg;
    }
}
