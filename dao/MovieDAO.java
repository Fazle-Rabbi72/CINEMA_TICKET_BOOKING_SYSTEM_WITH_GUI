package dao;

import db.DatabaseConnection;
import manager.Movie;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC Data Access Object for movie catalog management.
 */
public class MovieDAO {

    private static final String BASE_SELECT =
            "SELECT id, title, genre, age_rating, language, duration_min, imdb_rating, "
                    + "synopsis, accent_color, is_active FROM movies";

    public List<Movie> findAllActive() throws SQLException {
        return queryList(BASE_SELECT + " WHERE is_active = 1 ORDER BY id ASC");
    }

    public List<Movie> findAll() throws SQLException {
        return queryList(BASE_SELECT + " ORDER BY id ASC");
    }

    public List<Movie> search(String query, String genre) throws SQLException {
        StringBuilder sql = new StringBuilder(BASE_SELECT + " WHERE is_active = 1");
        boolean hasQuery = query != null && !query.isBlank();
        boolean hasGenre = genre != null && !genre.isBlank() && !"All Genres".equalsIgnoreCase(genre);

        if (hasQuery) {
            sql.append(" AND (title LIKE ? OR synopsis LIKE ? OR genre LIKE ?)");
        }
        if (hasGenre) {
            sql.append(" AND genre = ?");
        }
        sql.append(" ORDER BY imdb_rating DESC, id ASC");

        List<Movie> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int idx = 1;
            if (hasQuery) {
                String p = "%" + query.trim() + "%";
                ps.setString(idx++, p);
                ps.setString(idx++, p);
                ps.setString(idx++, p);
            }
            if (hasGenre) {
                ps.setString(idx, genre.trim());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public Movie findById(int id) throws SQLException {
        String sql = BASE_SELECT + " WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public int insert(Movie movie) throws SQLException {
        String sql = "INSERT INTO movies (title, genre, age_rating, language, duration_min, "
                + "imdb_rating, synopsis, accent_color, is_active) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, movie.getTitle().trim());
            ps.setString(2, movie.getGenre().trim());
            ps.setString(3, movie.getAgeRating().trim());
            ps.setString(4, movie.getLanguage().trim());
            ps.setInt(5, movie.getDurationMin());
            ps.setDouble(6, movie.getImdbRating());
            ps.setString(7, movie.getSynopsis().trim());
            ps.setString(8, movie.getAccentColor().trim());
            ps.setBoolean(9, movie.isActive());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    movie.setId(id);
                    return id;
                }
            }
        }
        return 0;
    }

    public void update(Movie movie) throws SQLException {
        String sql = "UPDATE movies SET title = ?, genre = ?, age_rating = ?, language = ?, "
                + "duration_min = ?, imdb_rating = ?, synopsis = ?, accent_color = ?, is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, movie.getTitle().trim());
            ps.setString(2, movie.getGenre().trim());
            ps.setString(3, movie.getAgeRating().trim());
            ps.setString(4, movie.getLanguage().trim());
            ps.setInt(5, movie.getDurationMin());
            ps.setDouble(6, movie.getImdbRating());
            ps.setString(7, movie.getSynopsis().trim());
            ps.setString(8, movie.getAccentColor().trim());
            ps.setBoolean(9, movie.isActive());
            ps.setInt(10, movie.getId());
            ps.executeUpdate();
        }
    }

    public void setActive(int movieId, boolean active) throws SQLException {
        String sql = "UPDATE movies SET is_active = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setInt(2, movieId);
            ps.executeUpdate();
        }
    }

    public int countActiveMovies() throws SQLException {
        String sql = "SELECT COUNT(*) FROM movies WHERE is_active = 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private List<Movie> queryList(String sql) throws SQLException {
        List<Movie> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    static Movie mapRow(ResultSet rs) throws SQLException {
        return new Movie(
                rs.getInt("id"),
                rs.getString("title"),
                rs.getString("genre"),
                rs.getString("age_rating"),
                rs.getString("language"),
                rs.getInt("duration_min"),
                rs.getDouble("imdb_rating"),
                rs.getString("synopsis"),
                rs.getString("accent_color"),
                rs.getBoolean("is_active")
        );
    }
}
