package com.fms.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Minimal notification writer used by Module 6 (FR-NOT-03). The notification panel,
 * unread counts and the scheduler arrive with Module 7; this only inserts rows.
 */
public class NotificationDAO {

    /**
     * Inserts a notification unless one with the same (user, ref_key) already exists (FR-NOT-06).
     * Runs on the caller's connection so it commits or rolls back with the surrounding transaction.
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
        }
    }
}