package manager;

import java.sql.Timestamp;

/**
 * Security and operational audit trail record.
 */
public class AuditLog {
    private final int id;
    private final Integer userId;
    private final String username;
    private final String action;
    private final String details;
    private final String severity;
    private final Timestamp createdAt;

    public AuditLog(int id, Integer userId, String username, String action,
                    String details, String severity, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.details = details;
        this.severity = severity;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public String getSeverity() {
        return severity;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }
}
