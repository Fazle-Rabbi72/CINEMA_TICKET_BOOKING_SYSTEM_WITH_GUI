package dao;

import db.DatabaseConnection;
import manager.SnackItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for concession snacks and combos.
 */
public class SnackDAO {

    public List<SnackItem> findAllAvailable() throws SQLException {
        String sql = "SELECT id, name, category, price, is_available FROM snacks WHERE is_available = 1 ORDER BY id ASC";
        List<SnackItem> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new SnackItem(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getDouble("price"),
                        rs.getBoolean("is_available")
                ));
            }
        }
        return list;
    }
}
