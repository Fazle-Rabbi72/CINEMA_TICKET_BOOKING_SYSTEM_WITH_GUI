package dao;

import db.DatabaseConnection;
import manager.AuditLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for security and operational audit logging.
 */
public class AuditLogDAO {

    public void log(Integer userId, String username, String action, String details, String severity) {
        try (Connection conn = DatabaseConnection.getConnection()) {
            log(conn, userId, username, action, details, severity);
        } catch (SQLException e) {
            System.err.println("AuditLog write failed: " + e.getMessage());
        }
    }

    public void log(Connection conn, Integer userId, String username, String action,
                    String details, String severity) throws SQLException {
        String sql = "INSERT INTO audit_logs (user_id, username, action, details, severity) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (userId == null || userId <= 0) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, truncate(username, 60));
            ps.setString(3, truncate(action, 60));
            ps.setString(4, truncate(details, 500));
            ps.setString(5, normalizeSeverity(severity));
            ps.executeUpdate();
        }
    }

    public List<AuditLog> findRecent(int limit, String severityFilter, String search) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, username, action, details, severity, created_at FROM audit_logs WHERE 1=1");
        boolean filterSeverity = severityFilter != null && !severityFilter.isBlank()
                && !"ALL".equalsIgnoreCase(severityFilter);
        boolean hasSearch = search != null && !search.isBlank();

        if (filterSeverity) {
            sql.append(" AND severity = ?");
        }
        if (hasSearch) {
            sql.append(" AND (username LIKE ? OR action LIKE ? OR details LIKE ?)");
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");

        List<AuditLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (filterSeverity) {
                ps.setString(idx++, severityFilter.trim().toUpperCase());
            }
            if (hasSearch) {
                String pattern = "%" + search.trim() + "%";
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
                ps.setString(idx++, pattern);
            }
            ps.setInt(idx, Math.max(1, Math.min(limit, 500)));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int uid = rs.getInt("user_id");
                    Integer userId = rs.wasNull() ? null : uid;
                    logs.add(new AuditLog(
                            rs.getInt("id"),
                            userId,
                            rs.getString("username"),
                            rs.getString("action"),
                            rs.getString("details"),
                            rs.getString("severity"),
                            rs.getTimestamp("created_at")
                    ));
                }
            }
        }
        return logs;
    }

    private static String normalizeSeverity(String severity) {
        if ("SECURITY".equalsIgnoreCase(severity)) return "SECURITY";
        if ("WARN".equalsIgnoreCase(severity)) return "WARN";
        return "INFO";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
