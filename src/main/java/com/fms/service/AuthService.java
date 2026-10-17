package com.fms.service;

import com.fms.dao.UserDAO;
import com.fms.exception.AuthenticationException;
import com.fms.model.Role;
import com.fms.model.User;
import com.fms.util.PasswordUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for authentication and user management.
 * Contains all business rules for login, lockout, and admin operations.
 *
 * Covers:
 * - FR-AUTH-01: Login with username and password.
 * - FR-AUTH-02: BCrypt password verification (never plain text).
 * - FR-AUTH-05: Role-based access checks.
 * - FR-AUTH-06: Admin creates, deactivates, resets passwords.
 * - FR-AUTH-07 / BR-11: Account lockout after 5 failed attempts for 5 minutes.
 */
public class AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 5;

    private final UserDAO userDAO;

    public AuthService() {
        this.userDAO = new UserDAO();
    }

    // ---------------------------------------------------------------
    // Login (FR-AUTH-01, FR-AUTH-02, FR-AUTH-07)
    // ---------------------------------------------------------------

    /**
     * Authenticates a user with their username and password.
     * Checks account status, lockout, and password match.
     * Increments failed attempts on wrong password.
     * Locks account after MAX_FAILED_ATTEMPTS failures.
     *
     * @param username The entered username
     * @param password The entered plain-text password
     * @return The authenticated User object
     * @throws AuthenticationException if login fails for any reason
     */
    public User login(String username, String password) throws AuthenticationException {
        // Step 1: Look up the user by username
        Optional<User> result = userDAO.findByUsername(username);

        if (result.isEmpty()) {
            logger.warn("Login failed: username '{}' not found", username);
            throw new AuthenticationException("Invalid username or password.");
        }

        User user = result.get();

        // Step 2: Check if the account is active
        if (!user.isActive()) {
            logger.warn("Login failed: account '{}' is deactivated", username);
            throw new AuthenticationException("This account has been deactivated. Contact your administrator.");
        }

        // Step 3: Check if the account is currently locked (FR-AUTH-07 / BR-11)
        if (PasswordUtil.isAccountLocked(user.getLockedUntil())) {
            logger.warn("Login failed: account '{}' is temporarily locked", username);
            throw new AuthenticationException(
                "Account is temporarily locked. Please try again after " + LOCKOUT_MINUTES + " minutes.",
                true
            );
        }

        // Step 4: Verify the password using BCrypt (FR-AUTH-02)
        boolean passwordMatch = PasswordUtil.checkPassword(password, user.getPasswordHash());

        if (!passwordMatch) {
            // Handle failed attempt
            handleFailedLogin(user);

            // Check if this failure triggered a lockout
            int attemptsNow = user.getFailedAttempts() + 1;
            if (attemptsNow >= MAX_FAILED_ATTEMPTS) {
                throw new AuthenticationException(
                    "Too many failed attempts. Account locked for " + LOCKOUT_MINUTES + " minutes.",
                    true
                );
            }

            int remaining = MAX_FAILED_ATTEMPTS - attemptsNow;
            throw new AuthenticationException(
                "Invalid username or password. " + remaining + " attempt(s) remaining."
            );
        }

        // Step 5: Successful login - reset any failed attempt counters
        if (user.getFailedAttempts() > 0 || user.getLockedUntil() != null) {
            userDAO.resetLockout(user.getUserId());
        }

        logger.info("User '{}' logged in successfully (role: {})", username, user.getRole());
        return user;
    }

    /**
     * Handles a failed login attempt for a user.
     * Increments the counter and triggers lockout at the threshold.
     */
    private void handleFailedLogin(User user) {
        int newCount = user.getFailedAttempts() + 1;

        if (newCount >= MAX_FAILED_ATTEMPTS) {
            // Lock the account for LOCKOUT_MINUTES (BR-11)
            Timestamp lockUntil = PasswordUtil.calculateLockoutTime(LOCKOUT_MINUTES);
            userDAO.lockAccount(user.getUserId(), lockUntil);
            logger.warn("Account '{}' locked after {} failed attempts", user.getUsername(), newCount);
        } else {
            // Just increment the failed attempts counter
            userDAO.updateFailedAttempts(user.getUserId(), newCount);
            logger.warn("Failed login for '{}': attempt {} of {}",
                    user.getUsername(), newCount, MAX_FAILED_ATTEMPTS);
        }
    }

    // ---------------------------------------------------------------
    // Admin User Management (FR-AUTH-06)
    // ---------------------------------------------------------------

    /**
     * Creates a new user account (Admin only - FR-AUTH-06).
     *
     * @param username  The new username
     * @param password  The plain-text password (will be hashed)
     * @param role      The role to assign
     * @param fullName  The user's full name
     * @param email     The user's email
     * @return The created User with generated ID
     * @throws IllegalArgumentException if username or email is already taken
     */
    public User createUser(String username, String password, Role role,
                           String fullName, String email) {

        // Validate that username is not already taken
        if (userDAO.isUsernameTaken(username)) {
            throw new IllegalArgumentException("Username '" + username + "' is already taken.");
        }

        // Validate that email is not already taken
        if (userDAO.isEmailTaken(email)) {
            throw new IllegalArgumentException("Email '" + email + "' is already registered.");
        }

        // Hash the password using BCrypt (FR-AUTH-02)
        String hashedPassword = PasswordUtil.hashPassword(password);

        // Create and save the user
        User newUser = new User(username, hashedPassword, role, fullName, email);
        User savedUser = userDAO.save(newUser);

        logger.info("Admin created new user: {} with role {}", username, role);
        return savedUser;
    }

    /**
     * Deactivates a user account (Admin only - FR-AUTH-06).
     * Uses soft-delete so the record stays in the database.
     *
     * @param userId The user ID to deactivate
     * @return true if the deactivation succeeded
     */
    public boolean deactivateUser(int userId) {
        boolean success = userDAO.delete(userId);
        if (success) {
            logger.info("Admin deactivated user ID: {}", userId);
        }
        return success;
    }

    /**
     * Resets a user's password (Admin only - FR-AUTH-06).
     * Also clears any lockout on the account.
     *
     * @param userId      The user ID
     * @param newPassword The new plain-text password (will be hashed)
     * @return true if the reset succeeded
     */
    public boolean resetPassword(int userId, String newPassword) {
        String hashedPassword = PasswordUtil.hashPassword(newPassword);
        boolean passwordUpdated = userDAO.updatePassword(userId, hashedPassword);

        if (passwordUpdated) {
            // Also clear any lockout so the user can log in immediately
            userDAO.resetLockout(userId);
            logger.info("Admin reset password for user ID: {}", userId);
        }
        return passwordUpdated;
    }

    // ---------------------------------------------------------------
    // User Queries
    // ---------------------------------------------------------------

    /**
     * Gets all users in the system.
     *
     * @return List of all users
     */
    public List<User> getAllUsers() {
        return userDAO.findAll();
    }

    /**
     * Gets a specific user by ID.
     *
     * @param userId The user ID
     * @return Optional containing the user if found
     */
    public Optional<User> getUserById(int userId) {
        return userDAO.findById(userId);
    }

    /**
     * Checks if a given role is allowed to perform an action (FR-AUTH-05).
     * Simple role hierarchy: Admin > Staff > User.
     *
     * @param userRole     The user's current role
     * @param requiredRole The minimum role required for the action
     * @return true if the user's role meets or exceeds the required role
     */
    public boolean hasPermission(Role userRole, Role requiredRole) {
        // Admin can do everything
        if (userRole == Role.Admin) {
            return true;
        }
        // Staff can do Staff and User level actions
        if (userRole == Role.Staff) {
            return requiredRole == Role.Staff || requiredRole == Role.User;
        }
        // User can only do User level actions
        return requiredRole == Role.User;
    }
}
