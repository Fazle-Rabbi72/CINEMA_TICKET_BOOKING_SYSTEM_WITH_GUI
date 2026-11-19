package manager;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persisted booking record with signed ticket metadata and itemized breakdown.
 */
public class Booking {
    private final int id;
    private final String bookingCode;
    private final int userId;
    private final String username;
    private final String customerFullName;
    private final int showId;
    private final String movieTitle;
    private final String hallName;
    private final LocalDate showDate;
    private final String showTime;
    private final String couponCode;
    private final double subtotal;
    private final double discountAmount;
    private final double snacksAmount;
    private final double totalAmount;
    private final String paymentMethod;
    private final String status;
    private final String ticketSignature;
    private final Timestamp createdAt;
    private final List<String> seatLabels;
    private final String snacksSummary;
    private final List<SeatInfo> seats;
    private final Map<SnackItem, Integer> snacks;

    public Booking(int id, String bookingCode, int userId, String username, String customerFullName,
                   int showId, String movieTitle, String hallName, LocalDate showDate, String showTime,
                   String couponCode, double subtotal, double discountAmount, double snacksAmount,
                   double totalAmount, String paymentMethod, String status, String ticketSignature,
                   Timestamp createdAt, List<String> seatLabels, String snacksSummary,
                   List<SeatInfo> seats, Map<SnackItem, Integer> snacks) {
        this.id = id;
        this.bookingCode = bookingCode;
        this.userId = userId;
        this.username = username;
        this.customerFullName = customerFullName;
        this.showId = showId;
        this.movieTitle = movieTitle;
        this.hallName = hallName;
        this.showDate = showDate;
        this.showTime = showTime;
        this.couponCode = couponCode;
        this.subtotal = subtotal;
        this.discountAmount = discountAmount;
        this.snacksAmount = snacksAmount;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.ticketSignature = ticketSignature;
        this.createdAt = createdAt;
        this.seatLabels = seatLabels == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(seatLabels));
        this.snacksSummary = snacksSummary == null ? "" : snacksSummary;
        this.seats = seats == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(seats));
        this.snacks = snacks == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(snacks));
    }

    public int getId() {
        return id;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getCustomerFullName() {
        return customerFullName;
    }

    public int getShowId() {
        return showId;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public String getHallName() {
        return hallName;
    }

    public LocalDate getShowDate() {
        return showDate;
    }

    public String getShowTime() {
        return showTime;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public double getDiscountAmount() {
        return discountAmount;
    }

    public double getSnacksAmount() {
        return snacksAmount;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getStatus() {
        return status;
    }

    public boolean isConfirmed() {
        return "CONFIRMED".equalsIgnoreCase(status);
    }

    public String getTicketSignature() {
        return ticketSignature;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public List<String> getSeatLabels() {
        return seatLabels;
    }

    public String getSeatLabelsDisplay() {
        return String.join(", ", seatLabels);
    }

    public String getSnacksSummary() {
        return snacksSummary;
    }

    public List<SeatInfo> getSeats() {
        return seats;
    }

    public Map<SnackItem, Integer> getSnacks() {
        return snacks;
    }
}
