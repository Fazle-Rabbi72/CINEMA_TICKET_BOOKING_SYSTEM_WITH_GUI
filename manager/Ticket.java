package manager;

import entity.*;
import interfaces.IBookingOperations;

public class Ticket implements IBookingOperations {

    private Customer customer;
    private int seats;

    public Ticket(Customer customer, int seats) {
        this.customer = customer;
        this.seats = seats;
    }

    @Override
    public void bookTicket() {
        double discount = (customer instanceof PremiumCustomer)
                ? ((PremiumCustomer) customer).getDiscount()
                : 0.0;

        double total = seats * 100 * (1 - discount);
        System.out.println("Booked by " + customer.getName());
        System.out.println("Total price: " + total);
    }

    @Override
    public void cancelTicket() {
        System.out.println("Ticket cancelled");
    }
}
