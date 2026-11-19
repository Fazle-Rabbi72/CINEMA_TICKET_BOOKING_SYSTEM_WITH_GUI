package security;

import java.util.regex.Pattern;

/**
 * Strict input validation and password strength evaluation.
 */
public final class InputValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,30}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[0-9\\s\\-]{8,18}$");
    private static final Pattern COUPON_PATTERN = Pattern.compile("^[A-Z0-9_\\-]{3,30}$");

    private InputValidator() {
    }

    public static final class StrengthResult {
        private final int score; // 0..4
        private final String label;

        public StrengthResult(int score, String label) {
            this.score = score;
            this.label = label;
        }

        public int getScore() {
            return score;
        }

        public String getLabel() {
            return label;
        }
    }

    public static boolean isValidUsername(String username) {
        return username != null && USERNAME_PATTERN.matcher(username.trim()).matches();
    }

    public static boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email.trim()).matches() && email.trim().length() <= 120;
    }

    public static boolean isValidPhone(String phone) {
        return phone != null && PHONE_PATTERN.matcher(phone.trim()).matches();
    }

    public static boolean isValidFullName(String fullName) {
        if (fullName == null) {
            return false;
        }
        String t = fullName.trim();
        return t.length() >= 2 && t.length() <= 100 && !t.contains("<") && !t.contains(">");
    }

    public static boolean isValidCouponCode(String code) {
        return code != null && COUPON_PATTERN.matcher(code.trim().toUpperCase()).matches();
    }

    public static StrengthResult passwordStrength(char[] password) {
        if (password == null || password.length == 0) {
            return new StrengthResult(0, "Empty");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else {
                hasSpecial = true;
            }
        }

        int categories = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        if (password.length < 6) {
            return new StrengthResult(1, "Weak");
        }
        if (password.length >= 10 && categories == 4) {
            return new StrengthResult(4, "Strong");
        }
        if (password.length >= 8 && categories >= 3) {
            return new StrengthResult(3, "Good");
        }
        if (categories >= 2) {
            return new StrengthResult(2, "Fair");
        }
        return new StrengthResult(1, "Weak");
    }

    public static String validatePassword(char[] password) {
        if (password == null || password.length < 8) {
            return "Password must be at least 8 characters long.";
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
            else hasSpecial = true;
        }
        int categories = (hasUpper ? 1 : 0) + (hasLower ? 1 : 0) + (hasDigit ? 1 : 0) + (hasSpecial ? 1 : 0);
        if (categories < 3) {
            return "Password must include at least 3 of: uppercase, lowercase, number, symbol.";
        }
        return null;
    }

    public static String validateSignup(String fullName, String username, String email,
                                      String phone, char[] password) {
        if (!isValidFullName(fullName)) {
            return "Full name must be 2-100 valid characters.";
        }
        if (!isValidUsername(username)) {
            return "Username must be 3-30 characters (letters, numbers, underscore only).";
        }
        if (!isValidEmail(email)) {
            return "Please enter a valid email address.";
        }
        if (!isValidPhone(phone)) {
            return "Please enter a valid phone number (8-18 digits).";
        }
        return validatePassword(password);
    }
}
