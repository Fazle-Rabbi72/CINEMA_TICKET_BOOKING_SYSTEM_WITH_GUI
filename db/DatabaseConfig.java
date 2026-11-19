package db;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads database and security configuration from external properties files
 * without hardcoding environment credentials in source code.
 */
public final class DatabaseConfig {

    private static final String PRIMARY_PATH = "config/db.properties";
    private static final String FALLBACK_PATH = "config/db.properties.example";
    private static final String DEFAULT_SECRET_FALLBACK =
            "5d9f482b1c7a3e6089142b5f7a0c3e8d9124a6f0b3c7e1d58942a0f6c3b7e2d1";

    private static final Properties PROPS = new Properties();

    static {
        load();
    }

    private DatabaseConfig() {
    }

    public static synchronized void load() {
        PROPS.clear();
        File primary = new File(PRIMARY_PATH);
        File target = primary.exists() ? primary : new File(FALLBACK_PATH);
        if (target.exists()) {
            try (InputStream in = new FileInputStream(target)) {
                PROPS.load(in);
            } catch (IOException e) {
                System.err.println("Warning: Could not read " + target.getPath() + ": " + e.getMessage());
            }
        }
    }

    public static String getHost() {
        return PROPS.getProperty("db.host", "localhost").trim();
    }

    public static String getPort() {
        return PROPS.getProperty("db.port", "3306").trim();
    }

    public static String getDatabaseName() {
        return PROPS.getProperty("db.name", "cinema_vault_db").trim();
    }

    public static String getUsername() {
        return PROPS.getProperty("db.user", "root").trim();
    }

    public static String getPassword() {
        return PROPS.getProperty("db.password", "");
    }

    public static String getParams() {
        return PROPS.getProperty(
                "db.params",
                "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Dhaka&createDatabaseIfNotExist=true"
        ).trim();
    }

    public static String getServerUrl() {
        String baseParams = getParams()
                .replace("&createDatabaseIfNotExist=true", "")
                .replace("createDatabaseIfNotExist=true&", "")
                .replace("createDatabaseIfNotExist=true", "");
        if (baseParams.isEmpty()) {
            baseParams = "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Dhaka";
        }
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/?" + baseParams;
    }

    public static String getDatabaseUrl() {
        String params = getParams();
        return "jdbc:mysql://" + getHost() + ":" + getPort() + "/" + getDatabaseName()
                + (params.isEmpty() ? "" : "?" + params);
    }

    public static String getHmacSecret() {
        String secret = PROPS.getProperty("security.hmac.secret", "").trim();
        if (secret.isEmpty() || secret.startsWith("CHANGE_ME")) {
            return DEFAULT_SECRET_FALLBACK;
        }
        return secret;
    }

    public static String getSocketHost() {
        String host = PROPS.getProperty("socket.host", "127.0.0.1").trim();
        return host.isEmpty() ? "127.0.0.1" : host;
    }

    public static int getSocketPort() {
        try {
            int p = Integer.parseInt(PROPS.getProperty("socket.port", "5555").trim());
            return (p > 0 && p <= 65535) ? p : 5555;
        } catch (NumberFormatException e) {
            return 5555;
        }
    }
}
