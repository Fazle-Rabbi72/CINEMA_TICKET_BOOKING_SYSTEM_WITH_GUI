package entity;

/**
 * Standard non-premium customer tier.
 */
public class RegularCustomer extends Customer {

    public RegularCustomer(String name) {
        super(name);
    }

    public RegularCustomer(int id, String name, String email) {
        super(id, name, email);
    }

    @Override
    public double getDiscount() {
        return 0.0;
    }

    @Override
    public String getTierName() {
        return "Standard Member";
    }

    @Override
    public String getPerkSummary() {
        return "Standard seat booking, digital E-Tickets & promo code eligibility";
    }
}
