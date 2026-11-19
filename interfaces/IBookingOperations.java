package interfaces;

/**
 * Core ticket booking contract implemented by Ticket.
 */
public interface IBookingOperations {
    void bookTicket();
    void cancelTicket();
    double calculateTotal();
    boolean verifyTicket();
}
