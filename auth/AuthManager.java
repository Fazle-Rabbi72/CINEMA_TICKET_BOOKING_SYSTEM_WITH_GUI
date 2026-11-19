package auth;

import dao.AuditLogDAO;
import dao.UserDAO;
import security.InputValidator;
import security.PasswordHasher;
import security.SessionManager;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * High-security authentication service with PBKDF2 hashing, strict validation,
 * brute-force account lockout, and audit trail logging.
 */
public final class AuthManager {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final int LOCKOUT_SECONDS = 120;

    private static final UserDAO USER_DAO = new UserDAO();
    private static final AuditLogDAO AUDIT_DAO = new AuditLogDAO();

    private AuthManager() {
    }

    public static final class AuthResult {
        private final boolean success;
        private final User user;
        private final String message;

        public AuthResult(boolean success, User user, String message) {
            this.success = success;
            this.user = user;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public User getUser() {
            return user;
        }

        public String getMessage() {
            return message;
        }
    }

    public static AuthResult signup(String fullName, String username, String email,
                                    String phone, char[] password) {
        try {
            String validationError = InputValidator.validateSignup(fullName, username, email, phone, password);
            if (validationError != null) {
                return new AuthResult(false, null, validationError);
            }

            String cleanUsername = username.trim();
            String cleanEmail = email.trim();
            String cleanFullName = fullName.trim();
            String cleanPhone = phone.trim();

            if (USER_DAO.existsByUsernameOrEmail(cleanUsername, cleanEmail)) {
                AUDIT_DAO.log(null, cleanUsername, "SIGNUP_DUPLICATE",
                        "Signup rejected: username or email already registered.", "WARN");
                return new AuthResult(false, null, "Username or email is already registered.");
            }

            String hash = PasswordHasher.hash(password);
            User newUser = new User(
                    0, cleanUsername, cleanFullName, cleanEmail, cleanPhone,
                    hash, "CUSTOMER", false, 0, null, null
            );
            User created = USER_DAO.createUser(newUser);
            AUDIT_DAO.log(created != null ? created.getId() : null, cleanUsername,
                    "SIGNUP", "New standard customer account created.", "INFO");
            return new AuthResult(true, created, "Account created! Sign in with your credentials.");
        } catch (SQLException e) {
            return new AuthResult(false, null, "Database error during signup: " + e.getMessage());
        } finally {
            PasswordHasher.wipe(password);
        }
    }

    public static AuthResult login(String username, char[] password) {
        try {
            if (username == null || username.isBlank() || password == null || password.length == 0) {
                return new AuthResult(false, null, "Please enter both username and password.");
            }
            String cleanUsername = username.trim();
            User user = USER_DAO.findByUsername(cleanUsername);
            if (user == null) {
                AUDIT_DAO.log(null, cleanUsername, "LOGIN_FAILED",
                        "Failed login attempt for unknown username.", "WARN");
                return new AuthResult(false, null, "Invalid username or password.");
            }

            if (user.isCurrentlyLocked()) {
                long secs = user.remainingLockSeconds();
                AUDIT_DAO.log(user.getId(), user.getUsername(), "LOGIN_BLOCKED_LOCKED",
                        "Login attempt blocked while account is locked (" + secs + "s remaining).", "SECURITY");
                return new AuthResult(false, null,
                        "Account temporarily locked after failed attempts. Retry in " + secs + "s.");
            }

            if (!PasswordHasher.verify(password, user.getPasswordHash())) {
                int attempts = user.getFailedAttempts() + 1;
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    Timestamp lockUntil = Timestamp.from(Instant.now().plusSeconds(LOCKOUT_SECONDS));
                    USER_DAO.recordFailedLogin(user.getId(), attempts, lockUntil);
                    AUDIT_DAO.log(user.getId(), user.getUsername(), "ACCOUNT_LOCKED",
                            "Account locked for " + LOCKOUT_SECONDS + "s after " + attempts + " failed logins.",
                            "SECURITY");
                    return new AuthResult(false, null,
                            "Too many failed attempts! Account locked for " + LOCKOUT_SECONDS + " seconds.");
                } else {
                    USER_DAO.recordFailedLogin(user.getId(), attempts, null);
                    int remaining = MAX_FAILED_ATTEMPTS - attempts;
                    AUDIT_DAO.log(user.getId(), user.getUsername(), "LOGIN_FAILED",
                            "Incorrect password (attempt " + attempts + " of " + MAX_FAILED_ATTEMPTS + ").",
                            "WARN");
                    return new AuthResult(false, null,
                            "Invalid username or password. (" + remaining + " attempt"
                                    + (remaining == 1 ? "" : "s") + " remaining)");
                }
            }

            USER_DAO.resetFailedAttempts(user.getId());
            User fresh = USER_DAO.findById(user.getId());
            if (fresh == null) {
                fresh = user;
            }
            SessionManager.login(fresh);
            AUDIT_DAO.log(fresh.getId(), fresh.getUsername(), "LOGIN_SUCCESS",
                    "Authenticated as " + fresh.getRole()
                            + (fresh.isPremium() ? " (VIP Gold)" : " (Standard)"), "INFO");
            return new AuthResult(true, fresh, "Welcome back, " + fresh.getFullName() + "!");
        } catch (SQLException e) {
            return new AuthResult(false, null, "Database error during login: " + e.getMessage());
        } finally {
            PasswordHasher.wipe(password);
        }
    }

    public static void logout() {
        User current = SessionManager.getCurrentUser();
        if (current != null) {
            AUDIT_DAO.log(current.getId(), current.getUsername(), "LOGOUT",
                    "User signed out.", "INFO");
        }
        SessionManager.logout();
    }

    public static User upgradeToPremium(User user) throws SQLException {
        if (user == null) {
            throw new IllegalArgumentException("User required.");
        }
        USER_DAO.updatePremiumStatus(user.getId(), true);
        user.setPremium(true);
        User fresh = USER_DAO.findById(user.getId());
        if (fresh != null) {
            SessionManager.refreshUser(fresh);
            AUDIT_DAO.log(fresh.getId(), fresh.getUsername(), "VIP_UPGRADED",
                    "Customer upgraded membership to VIP Gold (20% instant discount).", "INFO");
            return fresh;
        }
        SessionManager.refreshUser(user);
        return user;
    }
}
