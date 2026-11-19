package db;

import security.PasswordHasher;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Ensures the MySQL/MariaDB database, schema, and initial demo seed data exist.
 */
public final class DatabaseInitializer {

    private DatabaseInitializer() {
    }

    public static void initialize() throws SQLException {
        createDatabaseIfMissing();
        try (Connection conn = DatabaseConnection.getConnection()) {
            createTables(conn);
            seedIfEmpty(conn);
        }
    }

    private static void createDatabaseIfMissing() throws SQLException {
        String dbName = DatabaseConfig.getDatabaseName().replaceAll("[^a-zA-Z0-9_]", "");
        if (dbName.isEmpty()) {
            dbName = "cinema_vault_db";
        }
        try (Connection serverConn = DatabaseConnection.getServerConnection();
             Statement st = serverConn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbName
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        String[] ddlStatements = {
                "CREATE TABLE IF NOT EXISTS users ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "username VARCHAR(30) NOT NULL UNIQUE,"
                        + "full_name VARCHAR(100) NOT NULL,"
                        + "email VARCHAR(120) NOT NULL UNIQUE,"
                        + "phone VARCHAR(25) NOT NULL,"
                        + "password_hash VARCHAR(255) NOT NULL,"
                        + "role ENUM('CUSTOMER','ADMIN') NOT NULL DEFAULT 'CUSTOMER',"
                        + "is_premium TINYINT(1) NOT NULL DEFAULT 0,"
                        + "failed_attempts INT NOT NULL DEFAULT 0,"
                        + "locked_until TIMESTAMP NULL DEFAULT NULL,"
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS movies ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "title VARCHAR(150) NOT NULL,"
                        + "genre VARCHAR(60) NOT NULL,"
                        + "age_rating VARCHAR(20) NOT NULL,"
                        + "language VARCHAR(40) NOT NULL,"
                        + "duration_min INT NOT NULL,"
                        + "imdb_rating DECIMAL(3,1) NOT NULL DEFAULT 8.0,"
                        + "synopsis TEXT NOT NULL,"
                        + "accent_color VARCHAR(16) NOT NULL DEFAULT '#E50914',"
                        + "is_active TINYINT(1) NOT NULL DEFAULT 1,"
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS shows ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "movie_id INT NOT NULL,"
                        + "hall_name VARCHAR(80) NOT NULL,"
                        + "show_date DATE NOT NULL,"
                        + "show_time VARCHAR(20) NOT NULL,"
                        + "total_rows INT NOT NULL DEFAULT 6,"
                        + "total_cols INT NOT NULL DEFAULT 8,"
                        + "base_price DECIMAL(10,2) NOT NULL DEFAULT 350.00,"
                        + "gold_price DECIMAL(10,2) NOT NULL DEFAULT 500.00,"
                        + "vip_price DECIMAL(10,2) NOT NULL DEFAULT 750.00,"
                        + "is_active TINYINT(1) NOT NULL DEFAULT 1,"
                        + "CONSTRAINT fk_shows_movie FOREIGN KEY (movie_id) REFERENCES movies(id) ON DELETE CASCADE,"
                        + "INDEX idx_shows_movie_date (movie_id, show_date)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS snacks ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "name VARCHAR(100) NOT NULL UNIQUE,"
                        + "category VARCHAR(50) NOT NULL,"
                        + "price DECIMAL(10,2) NOT NULL,"
                        + "is_available TINYINT(1) NOT NULL DEFAULT 1"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS coupons ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "code VARCHAR(30) NOT NULL UNIQUE,"
                        + "discount_percent DECIMAL(5,2) NOT NULL,"
                        + "max_uses INT NOT NULL DEFAULT 100,"
                        + "used_count INT NOT NULL DEFAULT 0,"
                        + "is_active TINYINT(1) NOT NULL DEFAULT 1,"
                        + "expires_at DATE NOT NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS bookings ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "booking_code VARCHAR(32) NOT NULL UNIQUE,"
                        + "user_id INT NOT NULL,"
                        + "show_id INT NOT NULL,"
                        + "coupon_code VARCHAR(30) NULL DEFAULT NULL,"
                        + "subtotal DECIMAL(10,2) NOT NULL,"
                        + "discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,"
                        + "snacks_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,"
                        + "total_amount DECIMAL(10,2) NOT NULL,"
                        + "payment_method VARCHAR(40) NOT NULL,"
                        + "status ENUM('CONFIRMED','CANCELLED') NOT NULL DEFAULT 'CONFIRMED',"
                        + "ticket_signature VARCHAR(64) NOT NULL,"
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT fk_bookings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,"
                        + "CONSTRAINT fk_bookings_show FOREIGN KEY (show_id) REFERENCES shows(id) ON DELETE CASCADE,"
                        + "INDEX idx_bookings_user (user_id),"
                        + "INDEX idx_bookings_show (show_id)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS booking_seats ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "booking_id INT NOT NULL,"
                        + "show_id INT NOT NULL,"
                        + "seat_row INT NOT NULL,"
                        + "seat_col INT NOT NULL,"
                        + "seat_label VARCHAR(10) NOT NULL,"
                        + "seat_tier ENUM('STANDARD','GOLD','VIP') NOT NULL DEFAULT 'STANDARD',"
                        + "seat_price DECIMAL(10,2) NOT NULL,"
                        + "is_active TINYINT(1) NULL DEFAULT 1,"
                        + "CONSTRAINT fk_bseats_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,"
                        + "CONSTRAINT fk_bseats_show FOREIGN KEY (show_id) REFERENCES shows(id) ON DELETE CASCADE,"
                        + "UNIQUE KEY uq_active_seat (show_id, seat_row, seat_col, is_active)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS booking_snacks ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "booking_id INT NOT NULL,"
                        + "snack_id INT NOT NULL,"
                        + "quantity INT NOT NULL,"
                        + "unit_price DECIMAL(10,2) NOT NULL,"
                        + "CONSTRAINT fk_bsnacks_booking FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,"
                        + "CONSTRAINT fk_bsnacks_snack FOREIGN KEY (snack_id) REFERENCES snacks(id) ON DELETE RESTRICT"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS audit_logs ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "user_id INT NULL DEFAULT NULL,"
                        + "username VARCHAR(60) NOT NULL,"
                        + "action VARCHAR(60) NOT NULL,"
                        + "details VARCHAR(500) NOT NULL,"
                        + "severity ENUM('INFO','WARN','SECURITY') NOT NULL DEFAULT 'INFO',"
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,"
                        + "INDEX idx_audit_severity_time (severity, created_at)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

                "CREATE TABLE IF NOT EXISTS notifications ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "user_id INT NOT NULL,"
                        + "type ENUM('BOOKED','CANCELLED','RESCHEDULED','SYSTEM') NOT NULL,"
                        + "title VARCHAR(120) NOT NULL,"
                        + "message VARCHAR(500) NOT NULL,"
                        + "booking_code VARCHAR(32) NULL DEFAULT NULL,"
                        + "is_read TINYINT(1) NOT NULL DEFAULT 0,"
                        + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + "CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,"
                        + "INDEX idx_notif_user_read (user_id, is_read, created_at)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
        };

        try (Statement st = conn.createStatement()) {
            for (String ddl : ddlStatements) {
                st.executeUpdate(ddl);
            }
        }
    }

    private static void seedIfEmpty(Connection conn) throws SQLException {
        boolean seededAnything = false;

        if (isTableEmpty(conn, "users")) {
            seedUsers(conn);
            seededAnything = true;
        }
        if (isTableEmpty(conn, "movies")) {
            seedMoviesAndShows(conn);
            seededAnything = true;
        } else if (isTableEmpty(conn, "shows")) {
            seedShowsForExistingMovies(conn);
            seededAnything = true;
        }
        if (isTableEmpty(conn, "snacks")) {
            seedSnacks(conn);
            seededAnything = true;
        }
        if (isTableEmpty(conn, "coupons")) {
            seedCoupons(conn);
            seededAnything = true;
        }

        if (seededAnything) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO audit_logs (user_id, username, action, details, severity) VALUES (NULL, ?, ?, ?, ?)")) {
                ps.setString(1, "SYSTEM");
                ps.setString(2, "DB_SEED");
                ps.setString(3, "Initialized CineVault schema and default catalog/users.");
                ps.setString(4, "INFO");
                ps.executeUpdate();
            }
        }
    }

    private static boolean isTableEmpty(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM `" + tableName + "`");
             ResultSet rs = ps.executeQuery()) {
            return !rs.next() || rs.getInt(1) == 0;
        }
    }

    private static void seedUsers(Connection conn) throws SQLException {
        String sql = "INSERT INTO users (username, full_name, email, phone, password_hash, role, is_premium) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            insertSeedUser(ps, "admin", "System Administrator", "admin@cinevault.local",
                    "+8801700000001", "Admin@1234", "ADMIN", true);
            insertSeedUser(ps, "vipuser", "Arafat Rahman", "vip@cinevault.local",
                    "+8801700000002", "Vip@12345", "CUSTOMER", true);
            insertSeedUser(ps, "guest", "Nadia Karim", "guest@cinevault.local",
                    "+8801700000003", "Guest@123", "CUSTOMER", false);
        }
    }

    private static void insertSeedUser(PreparedStatement ps, String username, String fullName,
                                       String email, String phone, String rawPassword,
                                       String role, boolean premium) throws SQLException {
        char[] pwd = rawPassword.toCharArray();
        try {
            ps.setString(1, username);
            ps.setString(2, fullName);
            ps.setString(3, email);
            ps.setString(4, phone);
            ps.setString(5, PasswordHasher.hash(pwd));
            ps.setString(6, role);
            ps.setBoolean(7, premium);
            ps.executeUpdate();
        } finally {
            PasswordHasher.wipe(pwd);
        }
    }

    private static void seedMoviesAndShows(Connection conn) throws SQLException {
        Object[][] movies = {
                {"Interstellar", "Sci-Fi", "PG-13", "English", 169, 8.7,
                        "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.", "#38BDF8"},
                {"The Dark Knight", "Action", "PG-13", "English", 152, 9.0,
                        "Batman faces the Joker, an anarchistic mastermind pushing Gotham City to the brink of chaos.", "#6366F1"},
                {"Inception", "Thriller", "PG-13", "English", 148, 8.8,
                        "A thief who steals corporate secrets through dream-sharing technology is tasked with planting an idea.", "#F59E0B"},
                {"Avengers: Endgame", "Action", "PG-13", "English", 181, 8.4,
                        "After the devastating events of Infinity War, the Avengers assemble once more to restore order.", "#8B5CF6"},
                {"Dune: Part Two", "Sci-Fi", "PG-13", "English", 166, 8.6,
                        "Paul Atreides unites with the Fremen on a warpath of revenge against the conspirators.", "#EA580C"},
                {"Spider-Man: No Way Home", "Action", "PG-13", "English", 148, 8.2,
                        "With Spider-Man's identity revealed, Peter asks Doctor Strange for help, tearing open the multiverse.", "#E50914"},
                {"Oppenheimer", "Drama", "R", "English", 180, 8.3,
                        "The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.", "#DC2626"},
                {"John Wick: Chapter 4", "Action", "R", "English", 169, 7.7,
                        "John Wick uncovers a path to defeating The High Table while facing a ruthless new enemy.", "#EC4899"},
                {"Avatar: The Way of Water", "Adventure", "PG-13", "English", 192, 7.6,
                        "Jake Sully lives with his newfound family on Pandora until a familiar threat returns.", "#06B6D4"},
                {"Gladiator", "Drama", "R", "English", 155, 8.5,
                        "A former Roman General sets out to exact vengeance against the corrupt emperor.", "#D97706"}
        };

        String movieSql = "INSERT INTO movies (title, genre, age_rating, language, duration_min, imdb_rating, synopsis, accent_color, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(movieSql, Statement.RETURN_GENERATED_KEYS)) {
            for (Object[] m : movies) {
                ps.setString(1, (String) m[0]);
                ps.setString(2, (String) m[1]);
                ps.setString(3, (String) m[2]);
                ps.setString(4, (String) m[3]);
                ps.setInt(5, (Integer) m[4]);
                ps.setDouble(6, (Double) m[5]);
                ps.setString(7, (String) m[6]);
                ps.setString(8, (String) m[7]);
                ps.executeUpdate();
            }
        }
        seedShowsForExistingMovies(conn);
    }

    private static void seedShowsForExistingMovies(Connection conn) throws SQLException {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        String[] halls = {"Hall 1 - IMAX 3D", "Hall 2 - Dolby Atmos", "Hall 3 - VIP Lounge"};
        String[] timesA = {"11:30 AM", "03:45 PM", "07:30 PM", "10:15 PM"};
        String[] timesB = {"01:15 PM", "05:00 PM", "08:45 PM"};

        String selectSql = "SELECT id FROM movies ORDER BY id ASC";
        String insertSql = "INSERT INTO shows (movie_id, hall_name, show_date, show_time, total_rows, total_cols, base_price, gold_price, vip_price, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";

        try (PreparedStatement sel = conn.prepareStatement(selectSql);
             ResultSet rs = sel.executeQuery();
             PreparedStatement ins = conn.prepareStatement(insertSql)) {
            int idx = 0;
            while (rs.next()) {
                int movieId = rs.getInt("id");
                String hall1 = halls[idx % halls.length];
                String hall2 = halls[(idx + 1) % halls.length];

                // Show 1: Today
                ins.setInt(1, movieId);
                ins.setString(2, hall1);
                ins.setDate(3, Date.valueOf(today));
                ins.setString(4, timesA[idx % timesA.length]);
                ins.setInt(5, 6);
                ins.setInt(6, 8);
                ins.setDouble(7, 350.00);
                ins.setDouble(8, 500.00);
                ins.setDouble(9, 750.00);
                ins.executeUpdate();

                // Show 2: Tomorrow (for first 6 movies -> 16 total shows)
                if (idx < 6) {
                    ins.setInt(1, movieId);
                    ins.setString(2, hall2);
                    ins.setDate(3, Date.valueOf(tomorrow));
                    ins.setString(4, timesB[idx % timesB.length]);
                    ins.setInt(5, 6);
                    ins.setInt(6, 8);
                    ins.setDouble(7, 350.00);
                    ins.setDouble(8, 500.00);
                    ins.setDouble(9, 750.00);
                    ins.executeUpdate();
                }
                idx++;
            }
        }
    }

    private static void seedSnacks(Connection conn) throws SQLException {
        Object[][] snacks = {
                {"Large Caramel Popcorn", "Popcorn", 280.00},
                {"Classic Salted Popcorn", "Popcorn", 220.00},
                {"Nachos & Cheese Dip", "Snacks", 320.00},
                {"Chilled Coke 500ml", "Beverage", 120.00},
                {"Iced Cold Coffee", "Beverage", 240.00},
                {"Couple Cine Combo", "Combo", 650.00}
        };
        String sql = "INSERT INTO snacks (name, category, price, is_available) VALUES (?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] s : snacks) {
                ps.setString(1, (String) s[0]);
                ps.setString(2, (String) s[1]);
                ps.setDouble(3, (Double) s[2]);
                ps.executeUpdate();
            }
        }
    }

    private static void seedCoupons(Connection conn) throws SQLException {
        Object[][] coupons = {
                {"WELCOME10", 10.00, 500},
                {"STUDENT15", 15.00, 300},
                {"CINE20", 20.00, 200}
        };
        String sql = "INSERT INTO coupons (code, discount_percent, max_uses, used_count, is_active, expires_at) "
                + "VALUES (?, ?, ?, 0, 1, ?)";
        Date expiry = Date.valueOf(LocalDate.of(2027, 12, 31));
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Object[] c : coupons) {
                ps.setString(1, (String) c[0]);
                ps.setDouble(2, (Double) c[1]);
                ps.setInt(3, (Integer) c[2]);
                ps.setDate(4, expiry);
                ps.executeUpdate();
            }
        }
    }
}
