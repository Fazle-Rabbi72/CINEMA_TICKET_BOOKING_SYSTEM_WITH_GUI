package dao;

import db.DatabaseConnection;
import manager.Notification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for persisted user notifications.
 */
public class NotificationDAO {

    public Notification create(int userId, String type, String title,
                               String message, String bookingCode) throws SQLException {
        String safeType = normalizeType(type);
        String safeTitle = truncate(title, 120);
        String safeMsg = truncate(message, 500);
        String safeCode = (bookingCode == null || bookingCode.isBlank()) ? null : truncate(bookingCode.trim(), 32);

        String sql = "INSERT INTO notifications (user_id, type, title, message, booking_code, is_read) "
                + "VALUES (?, ?, ?, ?, ?, 0)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, safeType);
            ps.setString(3, safeTitle);
            ps.setString(4, safeMsg);
            if (safeCode == null) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, safeCode);
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    Notification loaded = findById(conn, id);
                    if (loaded != null) {
                        return loaded;
                    }
                    return new Notification(id, userId, safeType, safeTitle, safeMsg,
                            safeCode, false, new Timestamp(System.currentTimeMillis()));
                }
            }
        }
        return new Notification(0, userId, safeType, safeTitle, safeMsg,
                safeCode, false, new Timestamp(System.currentTimeMillis()));
    }

    public List<Notification> findByUser(int userId, int limit) throws SQLException {
        String sql = "SELECT id, user_id, type, title, message, booking_code, is_read, created_at "
                + "FROM notifications WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT ?";
        List<Notification> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setInt(2, Math.max(1, Math.min(limit, 200)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public int countUnread(int userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public void markRead(int notificationId, int userId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, notificationId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public void markAllRead(int userId) throws SQLException {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    private Notification findById(Connection conn, int id) throws SQLException {
        String sql = "SELECT id, user_id, type, title, message, booking_code, is_read, created_at "
                + "FROM notifications WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    private static Notification mapRow(ResultSet rs) throws SQLException {
        return new Notification(
                rs.getInt("id"),
                rs.getInt("user_id"),
                rs.getString("type"),
                rs.getString("title"),
                rs.getString("message"),
                rs.getString("booking_code"),
                rs.getBoolean("is_read"),
                rs.getTimestamp("created_at")
        );
    }

    private static String normalizeType(String type) {
        if ("BOOKED".equalsIgnoreCase(type)) return "BOOKED";
        if ("CANCELLED".equalsIgnoreCase(type)) return "CANCELLED";
        if ("RESCHEDULED".equalsIgnoreCase(type)) return "RESCHEDULED";
        return "SYSTEM";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
