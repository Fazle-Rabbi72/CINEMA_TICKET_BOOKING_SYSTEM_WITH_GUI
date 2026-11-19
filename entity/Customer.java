package entity;

/**
 * Base customer entity supporting polymorphic membership discount behavior.
 */
public class Customer {
    private final int id;
    private final String name;
    private final String email;

    public Customer(String name) {
        this(0, name, "");
    }

    public Customer(int id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public double getDiscount() {
        return 0.0;
    }

    public String getTierName() {
        return "Standard Member";
    }

    public String getPerkSummary() {
        return "Standard ticket booking and digital E-Tickets";
    }
}
