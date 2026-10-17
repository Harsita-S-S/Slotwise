package com.fms.dao;

import com.fms.model.Role;
import com.fms.model.User;
import com.fms.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for user accounts.
 * Handles all direct database operations on the 'users' table.
 *
 * Covers:
 * - FR-AUTH-01: findByUsername for login lookup.
 * - FR-AUTH-02: Stores BCrypt password hash via save/update.
 * - FR-AUTH-06: Admin CRUD operations (save, deactivate, resetPassword).
 * - FR-AUTH-07 / BR-11: updateFailedAttempts, lockAccount, resetLockout.
 */
public class UserDAO implements CrudDAO<User> {

    private static final Logger logger = LoggerFactory.getLogger(UserDAO.class);

    // ---------------------------------------------------------------
    // Helper: Map a ResultSet row to a User object
    // ---------------------------------------------------------------
    private User mapRowToUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("user_id"));
        user.setUsername(rs.getString("username"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setRole(Role.valueOf(rs.getString("role")));
        user.setFullName(rs.getString("full_name"));
        user.setEmail(rs.getString("email"));
        user.setActive(rs.getBoolean("is_active"));
        user.setFailedAttempts(rs.getInt("failed_attempts"));
        user.setLockedUntil(rs.getTimestamp("locked_until"));
        user.setCreatedAt(rs.getTimestamp("created_at"));
        return user;
    }

    // ---------------------------------------------------------------
    // CrudDAO interface methods
    // ---------------------------------------------------------------

    @Override
    public Optional<User> findById(int id) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding user by ID {}: {}", id, e.getMessage());
        }
        return Optional.empty();
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY user_id";
        List<User> users = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                users.add(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            logger.error("Error fetching all users: {}", e.getMessage());
        }
        return users;
    }

    @Override
    public User save(User user) {
        String sql = "INSERT INTO users (username, password_hash, role, full_name, email, is_active) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getPasswordHash());
            pstmt.setString(3, user.getRole().name());
            pstmt.setString(4, user.getFullName());
            pstmt.setString(5, user.getEmail());
            pstmt.setBoolean(6, user.isActive());

            int rowsInserted = pstmt.executeUpdate();

            if (rowsInserted > 0) {
                ResultSet keys = pstmt.getGeneratedKeys();
                if (keys.next()) {
                    user.setUserId(keys.getInt(1));
                }
                logger.info("Created new user: {} with role {}", user.getUsername(), user.getRole());
            }
        } catch (SQLException e) {
            logger.error("Error saving user {}: {}", user.getUsername(), e.getMessage());
        }
        return user;
    }

    @Override
    public boolean update(User user) {
        String sql = "UPDATE users SET username = ?, role = ?, full_name = ?, email = ?, is_active = ? " +
                     "WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, user.getUsername());
            pstmt.setString(2, user.getRole().name());
            pstmt.setString(3, user.getFullName());
            pstmt.setString(4, user.getEmail());
            pstmt.setBoolean(5, user.isActive());
            pstmt.setInt(6, user.getUserId());

            int rowsUpdated = pstmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            logger.error("Error updating user {}: {}", user.getUserId(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        // Soft-delete: deactivate instead of removing from the database
        String sql = "UPDATE users SET is_active = FALSE WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            int rowsUpdated = pstmt.executeUpdate();

            if (rowsUpdated > 0) {
                logger.info("Deactivated user with ID: {}", id);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error deactivating user {}: {}", id, e.getMessage());
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Auth-specific methods
    // ---------------------------------------------------------------

    /**
     * Finds a user by their username. Used during login (FR-AUTH-01).
     *
     * @param username The username to look up
     * @return Optional containing the User if found, empty otherwise
     */
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(mapRowToUser(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding user by username '{}': {}", username, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Updates failed login attempt count for a user (FR-AUTH-07).
     *
     * @param userId         The user's ID
     * @param failedAttempts New failed attempts count
     * @return true if the update succeeded
     */
    public boolean updateFailedAttempts(int userId, int failedAttempts) {
        String sql = "UPDATE users SET failed_attempts = ? WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, failedAttempts);
            pstmt.setInt(2, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error updating failed attempts for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Locks a user account until the given timestamp (FR-AUTH-07 / BR-11).
     *
     * @param userId      The user's ID
     * @param lockedUntil Timestamp when the lockout expires
     * @return true if the update succeeded
     */
    public boolean lockAccount(int userId, Timestamp lockedUntil) {
        String sql = "UPDATE users SET locked_until = ?, failed_attempts = 0 WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setTimestamp(1, lockedUntil);
            pstmt.setInt(2, userId);

            boolean success = pstmt.executeUpdate() > 0;
            if (success) {
                logger.warn("Account locked for user ID {} until {}", userId, lockedUntil);
            }
            return success;
        } catch (SQLException e) {
            logger.error("Error locking account for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Clears lockout status and resets failed attempts after a successful login.
     *
     * @param userId The user's ID
     * @return true if the update succeeded
     */
    public boolean resetLockout(int userId) {
        String sql = "UPDATE users SET failed_attempts = 0, locked_until = NULL WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Error resetting lockout for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Updates the password hash for a user (FR-AUTH-06: Admin resets passwords).
     *
     * @param userId       The user's ID
     * @param newPasswordHash The new BCrypt hash
     * @return true if the update succeeded
     */
    public boolean updatePassword(int userId, String newPasswordHash) {
        String sql = "UPDATE users SET password_hash = ? WHERE user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newPasswordHash);
            pstmt.setInt(2, userId);

            boolean success = pstmt.executeUpdate() > 0;
            if (success) {
                logger.info("Password updated for user ID: {}", userId);
            }
            return success;
        } catch (SQLException e) {
            logger.error("Error updating password for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a username already exists in the database.
     *
     * @param username The username to check
     * @return true if the username is taken
     */
    public boolean isUsernameTaken(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("Error checking username '{}': {}", username, e.getMessage());
        }
        return false;
    }

    /**
     * Checks if an email already exists in the database.
     *
     * @param email The email to check
     * @return true if the email is taken
     */
    public boolean isEmailTaken(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            logger.error("Error checking email '{}': {}", email, e.getMessage());
        }
        return false;
    }
}
