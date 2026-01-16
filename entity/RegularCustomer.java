package entity;

public class RegularCustomer extends Customer {

    public RegularCustomer(String name) {
        super(name);
    }

    public double getDiscount() {
        return 0.0;
    }
}
