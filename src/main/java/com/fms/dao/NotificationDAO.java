package com.fms.dao;

import com.fms.model.Notification;
import com.fms.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for in-app notifications (FR-NOT-03, FR-NOT-04, FR-NOT-06).
 * Writes take the caller's connection so they commit or roll back with the surrounding transaction.
 */
public class NotificationDAO {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDAO.class);

    /**
     * Inserts a notification unless one with the same (user, ref_key) already exists (FR-NOT-06).
     * A null refKey is never treated as a duplicate. The unique key on (user_id, ref_key) is the final
     * guard, so two threads racing on the same event still produce a single row.
     *
     * @return true if a row was inserted, false if it was a duplicate
     */
    public boolean insertIfAbsent(Connection conn, int userId, String message, String type, String refKey)
            throws SQLException {
        if (refKey != null) {
            String check = "SELECT 1 FROM notifications WHERE user_id = ? AND ref_key = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(check)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, refKey);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return false;
                    }
                }
            }
        }
        String insert = "INSERT INTO notifications (user_id, message, type, ref_key) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, message);
            pstmt.setString(3, type);
            pstmt.setString(4, refKey);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (refKey != null && (e.getErrorCode() == 1062 || "23000".equals(e.getSQLState()))) {
                return false; // lost a race against another writer: the event is already notified
            }
            throw e;
        }
    }

    /** Notifications for one user, newest first. */
    public List<Notification> findByUser(int userId, boolean unreadOnly, int limit) {
        List<Notification> list = new ArrayList<>();
        String sql = "SELECT notif_id, user_id, message, type, ref_key, is_read, created_at " +
                     "FROM notifications WHERE user_id = ? " +
                     (unreadOnly ? "AND is_read = FALSE " : "") +
                     "ORDER BY created_at DESC, notif_id DESC LIMIT ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setInt(2, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error loading notifications for user ID {}: {}", userId, e.getMessage(), e);
        }
        return list;
    }

    public int countUnread(int userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Error counting unread notifications for user ID {}: {}", userId, e.getMessage(), e);
        }
        return 0;
    }

    /** Marks one notification read. Returns false if it is not this user's or was already read. */
    public boolean markRead(int notifId, int userId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE notif_id = ? AND user_id = ? AND is_read = FALSE";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, notifId);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error marking notification {} read: {}", notifId, e.getMessage(), e);
            return false;
        }
    }

    /** Marks all of a user's unread notifications read; returns how many changed. */
    public int markAllRead(int userId) {
        String sql = "UPDATE notifications SET is_read = TRUE WHERE user_id = ? AND is_read = FALSE";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            return pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Error marking all notifications read for user ID {}: {}", userId, e.getMessage(), e);
            return 0;
        }
    }

    private Notification mapRow(ResultSet rs) throws SQLException {
        Notification n = new Notification();
        n.setNotifId(rs.getInt("notif_id"));
        n.setUserId(rs.getInt("user_id"));
        n.setMessage(rs.getString("message"));
        n.setType(rs.getString("type"));
        n.setRefKey(rs.getString("ref_key"));
        n.setRead(rs.getBoolean("is_read"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            n.setCreatedAt(created.toLocalDateTime());
        }
        return n;
    }
}
