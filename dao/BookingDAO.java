package dao;

import auth.User;
import db.DatabaseConnection;
import entity.Customer;
import manager.Booking;
import manager.Coupon;
import manager.Movie;
import manager.SeatInfo;
import manager.Show;
import manager.SnackItem;
import security.TicketSigner;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JDBC Data Access Object handling ACID transactional ticket bookings,
 * double-booking prevention, cancellations, HMAC verification, and analytics.
 */
public class BookingDAO {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    public static class SeatAlreadyBookedException extends RuntimeException {
        public SeatAlreadyBookedException(String message) {
            super(message);
        }
    }

    public static final class PricingBreakdown {
        private final double seatSubtotal;
        private final double memberDiscount;
        private final double couponDiscount;
        private final double totalDiscount;
        private final double snacksSubtotal;
        private final double grandTotal;

        public PricingBreakdown(double seatSubtotal, double memberDiscount, double couponDiscount,
                                double totalDiscount, double snacksSubtotal, double grandTotal) {
            this.seatSubtotal = seatSubtotal;
            this.memberDiscount = memberDiscount;
            this.couponDiscount = couponDiscount;
            this.totalDiscount = totalDiscount;
            this.snacksSubtotal = snacksSubtotal;
            this.grandTotal = grandTotal;
        }

        public double getSeatSubtotal() {
            return seatSubtotal;
        }

        public double getMemberDiscount() {
            return memberDiscount;
        }

        public double getCouponDiscount() {
            return couponDiscount;
        }

        public double getTotalDiscount() {
            return totalDiscount;
        }

        public double getSnacksSubtotal() {
            return snacksSubtotal;
        }

        public double getGrandTotal() {
            return grandTotal;
        }
    }

    public static final class VerificationResult {
        private final boolean found;
        private final boolean authentic;
        private final boolean confirmed;
        private final Booking booking;
        private final String summary;

        public VerificationResult(boolean found, boolean authentic, boolean confirmed,
                                  Booking booking, String summary) {
            this.found = found;
            this.authentic = authentic;
            this.confirmed = confirmed;
            this.booking = booking;
            this.summary = summary;
        }

        public boolean isFound() {
            return found;
        }

        public boolean isAuthentic() {
            return authentic;
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public Booking getBooking() {
            return booking;
        }

        public String getSummary() {
            return summary;
        }
    }

    public static final class HallOccupancy {
        private final String hallName;
        private final int totalCapacity;
        private final int bookedSeats;

        public HallOccupancy(String hallName, int totalCapacity, int bookedSeats) {
            this.hallName = hallName;
            this.totalCapacity = totalCapacity;
            this.bookedSeats = bookedSeats;
        }

        public String getHallName() {
            return hallName;
        }

        public int getTotalCapacity() {
            return totalCapacity;
        }

        public int getBookedSeats() {
            return bookedSeats;
        }

        public double getOccupancyPercent() {
            return totalCapacity <= 0 ? 0.0 : (bookedSeats * 100.0) / totalCapacity;
        }
    }

    public static PricingBreakdown calculatePricing(Customer customer, Show show,
                                                    List<SeatInfo> seats,
                                                    Map<SnackItem, Integer> snacks,
                                                    Coupon coupon) {
        double seatSubtotal = 0.0;
        if (seats != null && show != null) {
            for (SeatInfo s : seats) {
                seatSubtotal += show.getSeatPrice(s.getRow());
            }
        }
        seatSubtotal = round2(seatSubtotal);

        double memberRate = customer != null ? customer.getDiscount() : 0.0;
        double memberDiscount = round2(seatSubtotal * memberRate);
        double afterMember = round2(Math.max(0.0, seatSubtotal - memberDiscount));

        double couponDiscount = 0.0;
        if (coupon != null && coupon.isValidNow() && coupon.getDiscountPercent() > 0) {
            couponDiscount = round2(afterMember * (coupon.getDiscountPercent() / 100.0));
        }

        double totalDiscount = round2(Math.min(seatSubtotal, memberDiscount + couponDiscount));

        double snacksSubtotal = 0.0;
        if (snacks != null) {
            for (Map.Entry<SnackItem, Integer> entry : snacks.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0) {
                    snacksSubtotal += entry.getKey().getPrice() * entry.getValue();
                }
            }
        }
        snacksSubtotal = round2(snacksSubtotal);

        double grandTotal = round2(Math.max(0.0, seatSubtotal - totalDiscount) + snacksSubtotal);
        return new PricingBreakdown(seatSubtotal, memberDiscount, couponDiscount,
                totalDiscount, snacksSubtotal, grandTotal);
    }

    public Booking createBooking(User user, Show show, List<SeatInfo> selectedSeats,
                                 Map<SnackItem, Integer> snacks, Coupon coupon,
                                 String paymentMethod) throws SQLException {
        if (user == null || user.getId() <= 0) {
            throw new IllegalArgumentException("Authenticated user required for booking.");
        }
        if (show == null || show.getId() <= 0) {
            throw new IllegalArgumentException("Valid show selection required.");
        }
        if (selectedSeats == null || selectedSeats.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one seat.");
        }

        String safePayment = (paymentMethod == null || paymentMethod.isBlank()) ? "Card" : paymentMethod.trim();

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Show authoritativeShow = fetchAuthoritativeShow(conn, show);
                if (authoritativeShow == null || !authoritativeShow.isActive()) {
                    throw new IllegalStateException("Selected show is no longer active.");
                }

                for (SeatInfo seat : selectedSeats) {
                    if (seat.getRow() < 0 || seat.getRow() >= authoritativeShow.getRows()
                            || seat.getCol() < 0 || seat.getCol() >= authoritativeShow.getCols()) {
                        throw new IllegalArgumentException("Invalid seat coordinates: " + seat.getLabel());
                    }
                }

                Coupon lockedCoupon = null;
                if (coupon != null && coupon.getCode() != null && !coupon.getCode().isBlank()) {
                    lockedCoupon = lockAndValidateCoupon(conn, coupon.getCode());
                    if (lockedCoupon == null) {
                        throw new IllegalStateException("Coupon '" + coupon.getCode() + "' is invalid, expired, or exhausted.");
                    }
                }

                Map<SnackItem, Integer> authoritativeSnacks = resolveAuthoritativeSnacks(conn, snacks);
                PricingBreakdown pricing = calculatePricing(
                        user.toCustomer(), authoritativeShow, selectedSeats, authoritativeSnacks, lockedCoupon);

                String bookingCode = generateBookingCode();
                List<String> seatLabels = new ArrayList<>();
                for (SeatInfo s : selectedSeats) {
                    seatLabels.add(Show.seatLabel(s.getRow(), s.getCol()));
                }

                String signature = TicketSigner.sign(
                        bookingCode, user.getId(), authoritativeShow.getId(),
                        seatLabels, pricing.getGrandTotal()
                );

                int bookingId;
                String insertBookingSql = "INSERT INTO bookings (booking_code, user_id, show_id, coupon_code, "
                        + "subtotal, discount_amount, snacks_amount, total_amount, payment_method, status, ticket_signature) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertBookingSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, bookingCode);
                    ps.setInt(2, user.getId());
                    ps.setInt(3, authoritativeShow.getId());
                    if (lockedCoupon == null) {
                        ps.setNull(4, Types.VARCHAR);
                    } else {
                        ps.setString(4, lockedCoupon.getCode());
                    }
                    ps.setDouble(5, pricing.getSeatSubtotal());
                    ps.setDouble(6, pricing.getTotalDiscount());
                    ps.setDouble(7, pricing.getSnacksSubtotal());
                    ps.setDouble(8, pricing.getGrandTotal());
                    ps.setString(9, safePayment);
                    ps.setString(10, signature);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("Failed to retrieve generated booking ID.");
                        }
                        bookingId = keys.getInt(1);
                    }
                }

                String insertSeatSql = "INSERT INTO booking_seats "
                        + "(booking_id, show_id, seat_row, seat_col, seat_label, seat_tier, seat_price, is_active) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(insertSeatSql)) {
                    for (SeatInfo s : selectedSeats) {
                        int r = s.getRow();
                        int c = s.getCol();
                        ps.setInt(1, bookingId);
                        ps.setInt(2, authoritativeShow.getId());
                        ps.setInt(3, r);
                        ps.setInt(4, c);
                        ps.setString(5, Show.seatLabel(r, c));
                        ps.setString(6, authoritativeShow.getSeatTier(r));
                        ps.setDouble(7, authoritativeShow.getSeatPrice(r));
                        ps.executeUpdate();
                    }
                } catch (SQLIntegrityConstraintViolationException dup) {
                    conn.rollback();
                    auditLogDAO.log(user.getId(), user.getUsername(), "DOUBLE_BOOKING_BLOCKED",
                            "Blocked concurrent/duplicate seat booking for show #" + authoritativeShow.getId(), "WARN");
                    throw new SeatAlreadyBookedException(
                            "One or more selected seats were just booked by another user. Please refresh the seat map.");
                }

                if (!authoritativeSnacks.isEmpty()) {
                    String insertSnackSql = "INSERT INTO booking_snacks (booking_id, snack_id, quantity, unit_price) "
                            + "VALUES (?, ?, ?, ?)";
                    try (PreparedStatement ps = conn.prepareStatement(insertSnackSql)) {
                        for (Map.Entry<SnackItem, Integer> entry : authoritativeSnacks.entrySet()) {
                            ps.setInt(1, bookingId);
                            ps.setInt(2, entry.getKey().getId());
                            ps.setInt(3, entry.getValue());
                            ps.setDouble(4, entry.getKey().getPrice());
                            ps.executeUpdate();
                        }
                    }
                }

                if (lockedCoupon != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE coupons SET used_count = used_count + 1 WHERE id = ?")) {
                        ps.setInt(1, lockedCoupon.getId());
                        ps.executeUpdate();
                    }
                }

                auditLogDAO.log(conn, user.getId(), user.getUsername(), "BOOKING_CREATED",
                        "Booked " + bookingCode + " (" + String.join(", ", seatLabels) + ") Total: BDT "
                                + String.format(Locale.US, "%.2f", pricing.getGrandTotal()),
                        "INFO");

                conn.commit();
                return findByCode(bookingCode);
            } catch (SQLException | RuntimeException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public void cancelBooking(int bookingId, User actingUser) throws SQLException {
        if (actingUser == null) {
            throw new SecurityException("Authentication required to cancel a booking.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int ownerId;
                String bookingCode;
                String status;
                String selSql = "SELECT id, booking_code, user_id, status FROM bookings WHERE id = ? FOR UPDATE";
                try (PreparedStatement ps = conn.prepareStatement(selSql)) {
                    ps.setInt(1, bookingId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            throw new IllegalArgumentException("Booking not found.");
                        }
                        ownerId = rs.getInt("user_id");
                        bookingCode = rs.getString("booking_code");
                        status = rs.getString("status");
                    }
                }

                if (ownerId != actingUser.getId() && !actingUser.isAdmin()) {
                    conn.rollback();
                    auditLogDAO.log(actingUser.getId(), actingUser.getUsername(), "UNAUTHORIZED_CANCEL",
                            "Attempted to cancel booking " + bookingCode + " belonging to user #" + ownerId,
                            "SECURITY");
                    throw new SecurityException("Unauthorized: You can only cancel your own tickets.");
                }

                if (!"CONFIRMED".equalsIgnoreCase(status)) {
                    conn.rollback();
                    throw new IllegalStateException("Booking " + bookingCode + " is already cancelled.");
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE bookings SET status = 'CANCELLED' WHERE id = ?")) {
                    ps.setInt(1, bookingId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE booking_seats SET is_active = NULL WHERE booking_id = ?")) {
                    ps.setInt(1, bookingId);
                    ps.executeUpdate();
                }

                auditLogDAO.log(conn, actingUser.getId(), actingUser.getUsername(), "BOOKING_CANCELLED",
                        "Cancelled booking " + bookingCode + " and released seats.", "INFO");

                conn.commit();
            } catch (SQLException | RuntimeException ex) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw ex;
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                }
            }
        }
    }

    public List<Booking> findByUser(int userId) throws SQLException {
        String sql = "SELECT b.*, u.username, u.full_name, m.title AS movie_title, "
                + "s.hall_name, s.show_date, s.show_time "
                + "FROM bookings b "
                + "JOIN users u ON b.user_id = u.id "
                + "JOIN shows s ON b.show_id = s.id "
                + "JOIN movies m ON s.movie_id = m.id "
                + "WHERE b.user_id = ? "
                + "ORDER BY b.created_at DESC, b.id DESC";

        List<Booking> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(hydrateBooking(conn, rs));
                }
            }
        }
        return list;
    }

    public Booking findByCode(String bookingCode) throws SQLException {
        if (bookingCode == null || bookingCode.isBlank()) {
            return null;
        }
        String sql = "SELECT b.*, u.username, u.full_name, m.title AS movie_title, "
                + "s.hall_name, s.show_date, s.show_time "
                + "FROM bookings b "
                + "JOIN users u ON b.user_id = u.id "
                + "JOIN shows s ON b.show_id = s.id "
                + "JOIN movies m ON s.movie_id = m.id "
                + "WHERE b.booking_code = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bookingCode.trim().toUpperCase(Locale.US));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? hydrateBooking(conn, rs) : null;
            }
        }
    }

    public List<Booking> findConfirmedBookingsByShow(int showId) throws SQLException {
        String sql = "SELECT b.*, u.username, u.full_name, m.title AS movie_title, "
                + "s.hall_name, s.show_date, s.show_time "
                + "FROM bookings b "
                + "JOIN users u ON b.user_id = u.id "
                + "JOIN shows s ON b.show_id = s.id "
                + "JOIN movies m ON s.movie_id = m.id "
                + "WHERE b.show_id = ? AND b.status = 'CONFIRMED' "
                + "ORDER BY b.id ASC";

        List<Booking> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, showId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(hydrateBooking(conn, rs));
                }
            }
        }
        return list;
    }

    public VerificationResult verifyBookingByCode(String bookingCode, User verifier) throws SQLException {
        String actor = verifier != null ? verifier.getUsername() : "SYSTEM";
        Integer actorId = verifier != null ? verifier.getId() : null;

        Booking b = findByCode(bookingCode);
        if (b == null) {
            auditLogDAO.log(actorId, actor, "TICKET_VERIFY_NOT_FOUND",
                    "Verification lookup failed for code: " + bookingCode, "WARN");
            return new VerificationResult(false, false, false, null,
                    "No booking record found for code '" + bookingCode + "'.");
        }

        boolean authentic = TicketSigner.verify(
                b.getBookingCode(), b.getUserId(), b.getShowId(),
                b.getSeatLabels(), b.getTotalAmount(), b.getTicketSignature()
        );
        boolean confirmed = b.isConfirmed();

        if (!authentic) {
            auditLogDAO.log(actorId, actor, "TICKET_TAMPER_DETECTED",
                    "HMAC signature mismatch on ticket " + b.getBookingCode(), "SECURITY");
            return new VerificationResult(true, false, confirmed, b,
                    "SECURITY ALERT: Digital HMAC-SHA256 signature does NOT match database record!");
        }

        auditLogDAO.log(actorId, actor, "TICKET_VERIFIED",
                "Verified authentic ticket " + b.getBookingCode() + " [" + b.getStatus() + "]", "INFO");

        String msg = confirmed
                ? "AUTHENTIC & VALID: Signature verified via HMAC-SHA256. Ready for entry."
                : "AUTHENTIC SIGNATURE, BUT CANCELLED: This ticket was cancelled and cannot be used for entry.";
        return new VerificationResult(true, true, confirmed, b, msg);
    }

    public double getTotalRevenue() throws SQLException {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM bookings WHERE status = 'CONFIRMED'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0.0;
        }
    }

    public int getActiveBookingCount() throws SQLException {
        String sql = "SELECT COUNT(*) FROM bookings WHERE status = 'CONFIRMED'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    public Map<String, Double> getRevenueByMovie() throws SQLException {
        String sql = "SELECT m.title, COALESCE(SUM(b.total_amount), 0) AS rev "
                + "FROM movies m "
                + "LEFT JOIN shows s ON m.id = s.movie_id "
                + "LEFT JOIN bookings b ON s.id = b.show_id AND b.status = 'CONFIRMED' "
                + "GROUP BY m.id, m.title "
                + "ORDER BY rev DESC, m.title ASC";
        Map<String, Double> result = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("title"), rs.getDouble("rev"));
            }
        }
        return result;
    }

    public List<HallOccupancy> getHallOccupancyStats() throws SQLException {
        String sql = "SELECT s.hall_name, "
                + "COALESCE(SUM(s.total_rows * s.total_cols), 0) AS total_cap, "
                + "COALESCE(SUM(booked_sub.cnt), 0) AS booked_cnt "
                + "FROM shows s "
                + "LEFT JOIN ("
                + "    SELECT show_id, COUNT(*) AS cnt FROM booking_seats WHERE is_active = 1 GROUP BY show_id"
                + ") booked_sub ON booked_sub.show_id = s.id "
                + "WHERE s.is_active = 1 "
                + "GROUP BY s.hall_name ORDER BY s.hall_name ASC";
        List<HallOccupancy> stats = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                stats.add(new HallOccupancy(
                        rs.getString("hall_name"),
                        rs.getInt("total_cap"),
                        rs.getInt("booked_cnt")
                ));
            }
        }
        return stats;
    }

    private Show fetchAuthoritativeShow(Connection conn, Show fallback) throws SQLException {
        String sql = "SELECT id, hall_name, show_date, show_time, total_rows, total_cols, "
                + "base_price, gold_price, vip_price, is_active FROM shows WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, fallback.getId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Date d = rs.getDate("show_date");
                Movie m = fallback.getMovie();
                return new Show(
                        rs.getInt("id"),
                        m,
                        rs.getString("hall_name"),
                        d == null ? fallback.getShowDate() : d.toLocalDate(),
                        rs.getString("show_time"),
                        rs.getInt("total_rows"),
                        rs.getInt("total_cols"),
                        rs.getDouble("base_price"),
                        rs.getDouble("gold_price"),
                        rs.getDouble("vip_price"),
                        rs.getBoolean("is_active"),
                        0
                );
            }
        }
    }

    private Coupon lockAndValidateCoupon(Connection conn, String code) throws SQLException {
        String sql = "SELECT id, code, discount_percent, max_uses, used_count, is_active, expires_at "
                + "FROM coupons WHERE code = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim().toUpperCase(Locale.US));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Date exp = rs.getDate("expires_at");
                Coupon c = new Coupon(
                        rs.getInt("id"),
                        rs.getString("code"),
                        rs.getDouble("discount_percent"),
                        rs.getInt("max_uses"),
                        rs.getInt("used_count"),
                        rs.getBoolean("is_active"),
                        exp == null ? null : exp.toLocalDate()
                );
                return c.isValidNow() ? c : null;
            }
        }
    }

    private Map<SnackItem, Integer> resolveAuthoritativeSnacks(Connection conn,
                                                               Map<SnackItem, Integer> requested) throws SQLException {
        Map<SnackItem, Integer> resolved = new LinkedHashMap<>();
        if (requested == null || requested.isEmpty()) {
            return resolved;
        }
        String sql = "SELECT id, name, category, price, is_available FROM snacks WHERE id = ? AND is_available = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Map.Entry<SnackItem, Integer> entry : requested.entrySet()) {
                SnackItem item = entry.getKey();
                Integer qty = entry.getValue();
                if (item == null || qty == null || qty <= 0) continue;
                ps.setInt(1, item.getId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        SnackItem authItem = new SnackItem(
                                rs.getInt("id"),
                                rs.getString("name"),
                                rs.getString("category"),
                                rs.getDouble("price"),
                                rs.getBoolean("is_available")
                        );
                        resolved.put(authItem, qty);
                    }
                }
            }
        }
        return resolved;
    }

    private Booking hydrateBooking(Connection conn, ResultSet rs) throws SQLException {
        int bookingId = rs.getInt("id");
        String bookingCode = rs.getString("booking_code");
        int userId = rs.getInt("user_id");
        String username = rs.getString("username");
        String fullName = rs.getString("full_name");
        int showId = rs.getInt("show_id");
        String movieTitle = rs.getString("movie_title");
        String hallName = rs.getString("hall_name");
        Date showDateVal = rs.getDate("show_date");
        String showTime = rs.getString("show_time");
        String couponCode = rs.getString("coupon_code");
        double subtotal = rs.getDouble("subtotal");
        double discountAmount = rs.getDouble("discount_amount");
        double snacksAmount = rs.getDouble("snacks_amount");
        double totalAmount = rs.getDouble("total_amount");
        String paymentMethod = rs.getString("payment_method");
        String status = rs.getString("status");
        String ticketSignature = rs.getString("ticket_signature");
        Timestamp createdAt = rs.getTimestamp("created_at");

        List<SeatInfo> seats = new ArrayList<>();
        List<String> seatLabels = new ArrayList<>();
        String seatSql = "SELECT seat_row, seat_col, seat_label, seat_tier, seat_price, is_active "
                + "FROM booking_seats WHERE booking_id = ? ORDER BY seat_row ASC, seat_col ASC";
        try (PreparedStatement ps = conn.prepareStatement(seatSql)) {
            ps.setInt(1, bookingId);
            try (ResultSet srs = ps.executeQuery()) {
                while (srs.next()) {
                    String lbl = srs.getString("seat_label");
                    seatLabels.add(lbl);
                    seats.add(new SeatInfo(
                            srs.getInt("seat_row"),
                            srs.getInt("seat_col"),
                            lbl,
                            srs.getString("seat_tier"),
                            srs.getDouble("seat_price"),
                            srs.getObject("is_active") != null,
                            userId,
                            username
                    ));
                }
            }
        }

        Map<SnackItem, Integer> snacks = new LinkedHashMap<>();
        List<String> snackSummaryParts = new ArrayList<>();
        String snackSql = "SELECT bs.quantity, bs.unit_price, sn.id, sn.name, sn.category, sn.is_available "
                + "FROM booking_snacks bs JOIN snacks sn ON bs.snack_id = sn.id "
                + "WHERE bs.booking_id = ? ORDER BY sn.id ASC";
        try (PreparedStatement ps = conn.prepareStatement(snackSql)) {
            ps.setInt(1, bookingId);
            try (ResultSet snrs = ps.executeQuery()) {
                while (snrs.next()) {
                    int qty = snrs.getInt("quantity");
                    String name = snrs.getString("name");
                    SnackItem item = new SnackItem(
                            snrs.getInt("id"),
                            name,
                            snrs.getString("category"),
                            snrs.getDouble("unit_price"),
                            snrs.getBoolean("is_available")
                    );
                    snacks.put(item, qty);
                    snackSummaryParts.add(name + " x" + qty);
                }
            }
        }

        return new Booking(
                bookingId,
                bookingCode,
                userId,
                username,
                fullName,
                showId,
                movieTitle,
                hallName,
                showDateVal == null ? java.time.LocalDate.now() : showDateVal.toLocalDate(),
                showTime,
                couponCode,
                subtotal,
                discountAmount,
                snacksAmount,
                totalAmount,
                paymentMethod,
                status,
                ticketSignature,
                createdAt,
                seatLabels,
                snackSummaryParts.isEmpty() ? "None" : String.join(", ", snackSummaryParts),
                seats,
                snacks
        );
    }

    private static String generateBookingCode() {
        byte[] bytes = new byte[3];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format(Locale.US, "%02X", b));
        }
        return "CV-" + Year.now().getValue() + "-" + hex;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
