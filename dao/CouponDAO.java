package dao;

import db.DatabaseConnection;
import manager.Coupon;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC Data Access Object for promotional coupon codes.
 */
public class CouponDAO {

    public Coupon findValidByCode(String code) throws SQLException {
        if (code == null || code.isBlank()) {
            return null;
        }
        String sql = "SELECT id, code, discount_percent, max_uses, used_count, is_active, expires_at "
                + "FROM coupons WHERE code = ? AND is_active = 1 AND expires_at >= CURDATE() AND used_count < max_uses";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Date exp = rs.getDate("expires_at");
                return new Coupon(
                        rs.getInt("id"),
                        rs.getString("code"),
                        rs.getDouble("discount_percent"),
                        rs.getInt("max_uses"),
                        rs.getInt("used_count"),
                        rs.getBoolean("is_active"),
                        exp == null ? null : exp.toLocalDate()
                );
            }
        }
    }
}
