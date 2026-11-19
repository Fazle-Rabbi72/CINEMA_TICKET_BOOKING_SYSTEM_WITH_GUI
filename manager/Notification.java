package manager;

import java.sql.Timestamp;

/**
 * Real-time user notification persisted in MySQL and pushed over TCP sockets.
 */
public class Notification {
    private final int id;
    private final int userId;
    private final String type;
    private final String title;
    private final String message;
    private final String bookingCode;
    private boolean read;
    private final Timestamp createdAt;

    public Notification(int id, int userId, String type, String title, String message,
                        String bookingCode, boolean read, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.bookingCode = bookingCode;
        this.read = read;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getBookingCode() {
        return bookingCode;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }
}
