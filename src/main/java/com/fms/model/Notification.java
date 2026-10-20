package com.fms.model;

import java.time.LocalDateTime;

/**
 * An in-app notification for one user (FR-NOT-03, FR-NOT-04).
 * refKey identifies the underlying event so the same event never notifies a user twice (FR-NOT-06).
 */
public class Notification {

    private int notifId;
    private int userId;
    private String message;
    private String type;
    private String refKey;
    private boolean read;
    private LocalDateTime createdAt;

    public Notification() {
    }

    public int getNotifId() { return notifId; }
    public void setNotifId(int notifId) { this.notifId = notifId; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /** Kept as text so rows with types outside {@link NotificationType} still load. */
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getRefKey() { return refKey; }
    public void setRefKey(String refKey) { this.refKey = refKey; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
