package manager;

import auth.User;
import dao.BookingDAO;
import entity.Customer;
import interfaces.IBookingOperations;
import security.TicketSigner;

import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ticket facade implementing IBookingOperations with polymorphic Customer pricing,
 * JDBC ACID persistence, and HMAC-SHA256 verification.
 */
public class Ticket implements IBookingOperations {

    private final User user;
    private final Customer customer;
    private final Show show;
    private final List<SeatInfo> seats;
    private final Map<SnackItem, Integer> snacks;
    private final Coupon coupon;
    private final String paymentMethod;
    private final BookingDAO bookingDAO = new BookingDAO();
    private Booking persistedBooking;

    public Ticket(User user, Show show, List<SeatInfo> seats,
                  Map<SnackItem, Integer> snacks, Coupon coupon, String paymentMethod) {
        this.user = user;
        this.customer = user != null ? user.toCustomer() : new Customer("Guest");
        this.show = show;
        this.seats = seats == null ? Collections.emptyList() : seats;
        this.snacks = snacks == null ? Collections.emptyMap() : new LinkedHashMap<>(snacks);
        this.coupon = coupon;
        this.paymentMethod = paymentMethod;
    }

    public Ticket(Booking existingBooking, User actingUser) {
        this.persistedBooking = existingBooking;
        this.user = actingUser;
        this.customer = actingUser != null ? actingUser.toCustomer() : new Customer("Guest");
        this.show = null;
        this.seats = existingBooking != null ? existingBooking.getSeats() : Collections.emptyList();
        this.snacks = existingBooking != null ? existingBooking.getSnacks() : Collections.emptyMap();
        this.coupon = null;
        this.paymentMethod = existingBooking != null ? existingBooking.getPaymentMethod() : "Card";
    }

    @Override
    public double calculateTotal() {
        if (persistedBooking != null) {
            return persistedBooking.getTotalAmount();
        }
        return BookingDAO.calculatePricing(customer, show, seats, snacks, coupon).getGrandTotal();
    }

    public BookingDAO.PricingBreakdown getPricingBreakdown() {
        return BookingDAO.calculatePricing(customer, show, seats, snacks, coupon);
    }

    @Override
    public void bookTicket() {
        try {
            persistedBooking = bookingDAO.createBooking(user, show, seats, snacks, coupon, paymentMethod);
        } catch (SQLException e) {
            throw new IllegalStateException("Database booking error: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelTicket() {
        if (persistedBooking == null) {
            throw new IllegalStateException("No persisted booking attached to this ticket.");
        }
        try {
            bookingDAO.cancelBooking(persistedBooking.getId(), user);
        } catch (SQLException e) {
            throw new IllegalStateException("Database cancellation error: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyTicket() {
        if (persistedBooking == null) {
            return false;
        }
        return TicketSigner.verify(
                persistedBooking.getBookingCode(),
                persistedBooking.getUserId(),
                persistedBooking.getShowId(),
                persistedBooking.getSeatLabels(),
                persistedBooking.getTotalAmount(),
                persistedBooking.getTicketSignature()
        );
    }

    public Booking getPersistedBooking() {
        return persistedBooking;
    }

    public Customer getCustomer() {
        return customer;
    }
}
