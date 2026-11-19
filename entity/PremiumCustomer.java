package entity;

/**
 * VIP Gold customer tier receiving an automatic 20% discount on seats.
 */
public class PremiumCustomer extends Customer {

    public PremiumCustomer(String name) {
        super(name);
    }

    public PremiumCustomer(int id, String name, String email) {
        super(id, name, email);
    }

    @Override
    public double getDiscount() {
        return 0.20;
    }

    @Override
    public String getTierName() {
        return "VIP Gold Member";
    }

    @Override
    public String getPerkSummary() {
        return "Automatic 20% discount on every ticket + priority VIP lounge perks";
    }
}
