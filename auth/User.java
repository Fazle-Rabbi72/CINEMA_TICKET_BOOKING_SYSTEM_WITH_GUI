package auth;

import entity.Customer;
import entity.PremiumCustomer;
import entity.RegularCustomer;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Domain model representing an authenticated CineVault user.
 */
public class User {
    private final int id;
    private final String username;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String passwordHash;
    private final String role;
    private boolean premium;
    private int failedAttempts;
    private Timestamp lockedUntil;
    private final Timestamp createdAt;

    public User(String username, String passwordHash, boolean premium) {
        this(0, username, username, "", "", passwordHash, "CUSTOMER", premium, 0, null, null);
    }

    public User(int id, String username, String fullName, String email, String phone,
                String passwordHash, String role, boolean premium,
                int failedAttempts, Timestamp lockedUntil, Timestamp createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.passwordHash = passwordHash;
        this.role = role == null ? "CUSTOMER" : role;
        this.premium = premium;
        this.failedAttempts = failedAttempts;
        this.lockedUntil = lockedUntil;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }

    public boolean isPremium() {
        return premium;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public Timestamp getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Timestamp lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public boolean isCurrentlyLocked() {
        return lockedUntil != null && lockedUntil.toInstant().isAfter(Instant.now());
    }

    public long remainingLockSeconds() {
        if (!isCurrentlyLocked()) {
            return 0;
        }
        long diff = lockedUntil.toInstant().getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(1, diff);
    }

    public Customer toCustomer() {
        String display = (fullName == null || fullName.isBlank()) ? username : fullName;
        return premium
                ? new PremiumCustomer(id, display, email)
                : new RegularCustomer(id, display, email);
    }
}
