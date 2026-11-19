package manager;

import java.util.Objects;

/**
 * Represents a concession snack or beverage item available during checkout.
 */
public class SnackItem {
    private final int id;
    private final String name;
    private final String category;
    private final double price;
    private final boolean available;

    public SnackItem(int id, String name, String category, double price, boolean available) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.available = available;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public double getPrice() {
        return price;
    }

    public boolean isAvailable() {
        return available;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SnackItem)) return false;
        SnackItem other = (SnackItem) o;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
