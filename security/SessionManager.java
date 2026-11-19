package security;

import auth.User;

import java.time.Instant;

/**
 * Tracks the active authenticated session and enforces role-based access control.
 */
public final class SessionManager {

    private static volatile User currentUser;
    private static volatile Instant loginTime;
    private static volatile Instant lastActivity;

    private SessionManager() {
    }

    public static synchronized void login(User user) {
        currentUser = user;
        Instant now = Instant.now();
        loginTime = now;
        lastActivity = now;
    }

    public static synchronized void logout() {
        currentUser = null;
        loginTime = null;
        lastActivity = null;
    }

    public static User getCurrentUser() {
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean isAdmin() {
        User u = currentUser;
        return u != null && u.isAdmin();
    }

    public static void requireAdmin() {
        if (!isAdmin()) {
            throw new SecurityException("Administrator privileges required.");
        }
    }

    public static synchronized void refreshUser(User updatedUser) {
        if (updatedUser != null && currentUser != null && currentUser.getId() == updatedUser.getId()) {
            currentUser = updatedUser;
        }
    }

    public static Instant getLoginTime() {
        return loginTime;
    }

    public static void touchActivity() {
        lastActivity = Instant.now();
    }

    public static Instant getLastActivity() {
        return lastActivity;
    }
}
