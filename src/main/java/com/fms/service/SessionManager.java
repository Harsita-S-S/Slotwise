package com.fms.service;

import com.fms.exception.AccessDeniedException;
import com.fms.model.Role;
import com.fms.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton session manager that holds the currently logged-in user.
 * Accessible from all controllers and screens throughout the application.
 *
 * Covers:
 * - FR-AUTH-04: Session object accessible to all screens until logout.
 * - FR-AUTH-05: Provides role checks to block unauthorized access.
 */
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    // Singleton instance
    private static SessionManager instance;

    // The currently logged-in user (null when no one is logged in)
    private User currentUser;

    /** Private constructor to prevent external instantiation */
    private SessionManager() {
        this.currentUser = null;
    }

    /**
     * Returns the singleton SessionManager instance.
     *
     * @return The SessionManager instance
     */
    public static synchronized SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    /**
     * Sets the current user after a successful login.
     *
     * @param user The authenticated user
     */
    public void setCurrentUser(User user) {
        this.currentUser = user;
        if (user != null) {
            logger.info("Session started for user: {} (role: {})", user.getUsername(), user.getRole());
        }
    }

    /**
     * Returns the currently logged-in user.
     *
     * @return The current User, or null if no one is logged in
     */
    public User getCurrentUser() {
        return this.currentUser;
    }

    /**
     * Checks if there is an active session (someone is logged in).
     *
     * @return true if a user is logged in
     */
    public boolean isLoggedIn() {
        return this.currentUser != null;
    }

    /**
     * Gets the role of the currently logged-in user.
     *
     * @return The user's Role, or null if no one is logged in
     */
    public Role getCurrentRole() {
        if (this.currentUser != null) {
            return this.currentUser.getRole();
        }
        return null;
    }

    /**
     * Gets the display name for the current user.
     *
     * @return The user's full name, or "Guest" if not logged in
     */
    public String getCurrentUserDisplayName() {
        if (this.currentUser != null) {
            return this.currentUser.getFullName();
        }
        return "Guest";
    }

    /**
     * Checks if the current user has the required role (FR-AUTH-05).
     * Throws AccessDeniedException if the check fails.
     *
     * @param requiredRole The minimum role required
     * @throws AccessDeniedException if the user does not have the required role
     */
    public void requireRole(Role requiredRole) {
        if (this.currentUser == null) {
            throw new AccessDeniedException("Not logged in. Please sign in first.");
        }

        Role userRole = this.currentUser.getRole();

        // Admin can access everything
        if (userRole == Role.Admin) {
            return;
        }

        // Staff can access Staff and User screens
        if (userRole == Role.Staff) {
            if (requiredRole == Role.Admin) {
                throw new AccessDeniedException(
                    "Access denied. This action requires Admin privileges."
                );
            }
            return;
        }

        // User can only access User-level screens
        if (requiredRole != Role.User) {
            throw new AccessDeniedException(
                "Access denied. You do not have permission for this action."
            );
        }
    }

    /**
     * Enforces that the current user is an Admin (FR-AUTH-05).
     * Throws AccessDeniedException if not logged in or not an Admin.
     */
    public void requireAdmin() {
        requireRole(Role.Admin);
    }

    /**
     * Enforces that the current user has at least Staff privileges (FR-AUTH-05).
     * Throws AccessDeniedException if not logged in or a regular User.
     */
    public void requireStaff() {
        requireRole(Role.Staff);
    }

    /**
     * Checks if the current user is an Admin.
     *
     * @return true if the current user has the Admin role
     */
    public boolean isAdmin() {
        return this.currentUser != null && this.currentUser.getRole() == Role.Admin;
    }

    /**
     * Checks if the current user is Staff.
     *
     * @return true if the current user has the Staff role
     */
    public boolean isStaff() {
        return this.currentUser != null && this.currentUser.getRole() == Role.Staff;
    }

    /**
     * Clears the session on logout.
     */
    public void logout() {
        if (this.currentUser != null) {
            logger.info("Session ended for user: {}", this.currentUser.getUsername());
        }
        this.currentUser = null;
    }
}
