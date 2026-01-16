package entity;

public class PremiumCustomer extends Customer {

    public PremiumCustomer(String name) {
        super(name);
    }

    public double getDiscount() {
        return 0.20; 
    }
}
