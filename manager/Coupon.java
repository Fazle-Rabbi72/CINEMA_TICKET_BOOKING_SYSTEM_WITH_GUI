package manager;

import java.time.LocalDate;

/**
 * Represents a promotional coupon code stored in the database.
 */
public class Coupon {
    private final int id;
    private final String code;
    private final double discountPercent;
    private final int maxUses;
    private final int usedCount;
    private final boolean active;
    private final LocalDate expiresAt;

    public Coupon(int id, String code, double discountPercent, int maxUses,
                  int usedCount, boolean active, LocalDate expiresAt) {
        this.id = id;
        this.code = code;
        this.discountPercent = discountPercent;
        this.maxUses = maxUses;
        this.usedCount = usedCount;
        this.active = active;
        this.expiresAt = expiresAt;
    }

    public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public double getDiscountPercent() {
        return discountPercent;
    }

    public int getMaxUses() {
        return maxUses;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public boolean isValidNow() {
        return active && usedCount < maxUses
                && (expiresAt == null || !expiresAt.isBefore(LocalDate.now()));
    }
}
