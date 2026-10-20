package com.fms.service;

import com.fms.dao.NotificationDAO;
import com.fms.exception.AccessDeniedException;
import com.fms.exception.InvalidInputException;
import com.fms.model.Notification;
import com.fms.model.NotificationType;
import com.fms.model.User;
import com.fms.util.DBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Creates and serves in-app notifications (SRS Module 7: FR-NOT-03, FR-NOT-04, FR-NOT-06).
 *
 * Event code (bookings, maintenance, the future scheduler) calls {@code notifyUser}; the notification
 * panel uses the "my notifications" methods, which only ever see the logged-in user's own rows.
 */
public class NotificationService {

    /** Most recent notifications shown in the panel. */
    public static final int PANEL_LIMIT = 200;
    private static final int MAX_REF_KEY_LENGTH = 100;

    private final NotificationDAO notificationDAO;

    public NotificationService() {
        this(new NotificationDAO());
    }

    public NotificationService(NotificationDAO notificationDAO) {
        this.notificationDAO = notificationDAO;
    }

    /**
     * Creates a notification on the caller's connection, so it commits or rolls back together with the
     * change that caused it. The refKey identifies the event; a second call with the same user and refKey
     * is ignored (FR-NOT-06). Pass a null refKey for events that may legitimately repeat.
     *
     * @return true if created, false if it was a duplicate
     */
    public boolean notifyUser(Connection conn, int userId, NotificationType type, String message, String refKey)
            throws SQLException {
        validate(userId, type, message, refKey);
        return notificationDAO.insertIfAbsent(conn, userId, message.trim(), type.name(), refKey);
    }

    /** Creates a notification in its own transaction (for callers that have no connection open). */
    public boolean notifyUser(int userId, NotificationType type, String message, String refKey) {
        validate(userId, type, message, refKey);
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            return notificationDAO.insertIfAbsent(conn, userId, message.trim(), type.name(), refKey);
        } catch (SQLException e) {
            throw new RuntimeException("Database error while creating notification", e);
        }
    }

    /** Newest first; limited to {@link #PANEL_LIMIT}. */
    public List<Notification> getMyNotifications(boolean unreadOnly) {
        return notificationDAO.findByUser(requireUser().getUserId(), unreadOnly, PANEL_LIMIT);
    }

    public int getMyUnreadCount() {
        return notificationDAO.countUnread(requireUser().getUserId());
    }

    /** Marks one of the user's own notifications read; false if not theirs or already read. */
    public boolean markRead(int notifId) {
        return notificationDAO.markRead(notifId, requireUser().getUserId());
    }

    /** Marks all of the user's notifications read; returns how many were unread. */
    public int markAllRead() {
        return notificationDAO.markAllRead(requireUser().getUserId());
    }

    private User requireUser() {
        User user = SessionManager.getInstance().getCurrentUser();
        if (user == null) {
            throw new AccessDeniedException("You must be logged in to view notifications.");
        }
        return user;
    }

    private void validate(int userId, NotificationType type, String message, String refKey) {
        if (userId <= 0) {
            throw new InvalidInputException("userId", "A recipient is required.");
        }
        if (type == null) {
            throw new InvalidInputException("type", "A notification type is required.");
        }
        if (message == null || message.isBlank()) {
            throw new InvalidInputException("message", "A notification message is required.");
        }
        if (refKey != null && refKey.length() > MAX_REF_KEY_LENGTH) {
            throw new InvalidInputException("refKey", "Event key must be at most " + MAX_REF_KEY_LENGTH + " characters.");
        }
    }
}
