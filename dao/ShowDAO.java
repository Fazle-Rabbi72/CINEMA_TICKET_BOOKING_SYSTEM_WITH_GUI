package dao;

import db.DatabaseConnection;
import manager.Movie;
import manager.SeatInfo;
import manager.Show;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for movie shows and live auditorium seat maps.
 */
public class ShowDAO {

    private static final String BASE_SELECT =
            "SELECT s.id AS s_id, s.movie_id, s.hall_name, s.show_date, s.show_time, "
                    + "s.total_rows, s.total_cols, s.base_price, s.gold_price, s.vip_price, s.is_active AS s_active, "
                    + "(SELECT COUNT(*) FROM booking_seats bs WHERE bs.show_id = s.id AND bs.is_active = 1) AS booked_count, "
                    + "m.id, m.title, m.genre, m.age_rating, m.language, m.duration_min, m.imdb_rating, "
                    + "m.synopsis, m.accent_color, m.is_active "
                    + "FROM shows s JOIN movies m ON s.movie_id = m.id";

    public List<Show> findActiveShowsByMovie(int movieId) throws SQLException {
        String sql = BASE_SELECT + " WHERE s.movie_id = ? AND s.is_active = 1 ORDER BY s.show_date ASC, s.id ASC";
        List<Show> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Show> findAllActiveShows() throws SQLException {
        String sql = BASE_SELECT + " WHERE s.is_active = 1 AND m.is_active = 1 ORDER BY s.show_date ASC, s.id ASC";
        return queryList(sql);
    }

    public List<Show> findAllShows() throws SQLException {
        String sql = BASE_SELECT + " ORDER BY s.show_date DESC, s.id DESC";
        return queryList(sql);
    }

    public Show findById(int id) throws SQLException {
        String sql = BASE_SELECT + " WHERE s.id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public int insert(Show show) throws SQLException {
        String sql = "INSERT INTO shows (movie_id, hall_name, show_date, show_time, "
                + "total_rows, total_cols, base_price, gold_price, vip_price, is_active) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, show.getMovie().getId());
            ps.setString(2, show.getHallName().trim());
            ps.setDate(3, Date.valueOf(show.getShowDate()));
            ps.setString(4, show.getShowTime().trim());
            ps.setInt(5, show.getRows());
            ps.setInt(6, show.getCols());
            ps.setDouble(7, show.getBasePrice());
            ps.setDouble(8, show.getGoldPrice());
            ps.setDouble(9, show.getVipPrice());
            ps.setBoolean(10, show.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    show.setId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public Show rescheduleShow(int showId, String newHall, java.time.LocalDate newDate, String newTime)
            throws SQLException {
        String sql = "UPDATE shows SET hall_name = ?, show_date = ?, show_time = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newHall.trim());
            ps.setDate(2, Date.valueOf(newDate));
            ps.setString(3, newTime.trim());
            ps.setInt(4, showId);
            ps.executeUpdate();
        }
        return findById(showId);
    }

    public void delete(int showId) throws SQLException {
        String sql = "DELETE FROM shows WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, showId);
            ps.executeUpdate();
        }
    }

    public SeatInfo[][] getSeatMap(Show show) throws SQLException {
        int rows = show.getRows();
        int cols = show.getCols();
        SeatInfo[][] map = new SeatInfo[rows][cols];

        for (int r = 0; r < rows; r++) {
            String tier = show.getSeatTier(r);
            double price = show.getSeatPrice(r);
            for (int c = 0; c < cols; c++) {
                map[r][c] = new SeatInfo(
                        r, c, Show.seatLabel(r, c), tier, price, false, 0, null
                );
            }
        }

        String sql = "SELECT bs.seat_row, bs.seat_col, b.user_id, u.username "
                + "FROM booking_seats bs "
                + "JOIN bookings b ON bs.booking_id = b.id "
                + "JOIN users u ON b.user_id = u.id "
                + "WHERE bs.show_id = ? AND bs.is_active = 1 AND b.status = 'CONFIRMED'";

        int booked = 0;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, show.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int r = rs.getInt("seat_row");
                    int c = rs.getInt("seat_col");
                    if (r >= 0 && r < rows && c >= 0 && c < cols) {
                        SeatInfo seat = map[r][c];
                        seat.setBooked(true);
                        seat.setBookedByUserId(rs.getInt("user_id"));
                        seat.setBookedByUsername(rs.getString("username"));
                        booked++;
                    }
                }
            }
        }
        show.setBookedCount(booked);
        return map;
    }

    private List<Show> queryList(String sql) throws SQLException {
        List<Show> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    private Show mapRow(ResultSet rs) throws SQLException {
        Movie movie = MovieDAO.mapRow(rs);
        Date d = rs.getDate("show_date");
        return new Show(
                rs.getInt("s_id"),
                movie,
                rs.getString("hall_name"),
                d == null ? java.time.LocalDate.now() : d.toLocalDate(),
                rs.getString("show_time"),
                rs.getInt("total_rows"),
                rs.getInt("total_cols"),
                rs.getDouble("base_price"),
                rs.getDouble("gold_price"),
                rs.getDouble("vip_price"),
                rs.getBoolean("s_active"),
                rs.getInt("booked_count")
        );
    }
}
