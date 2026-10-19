package com.fms;

import com.fms.dao.UserDAO;
import com.fms.exception.AccessDeniedException;
import com.fms.exception.AuthenticationException;
import com.fms.model.Role;
import com.fms.model.User;
import com.fms.service.AuthService;
import com.fms.service.SessionManager;
import com.fms.util.DatabaseInitializer;
import com.fms.util.PasswordUtil;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Authentication and Session Management module.
 *
 * Covers:
 * - FR-AUTH-01: Login credential verification
 * - FR-AUTH-02: BCrypt hashed passwords
 * - FR-AUTH-03 & FR-AUTH-04: Session state persistence
 * - FR-AUTH-05: Role hierarchy and permission enforcement
 * - FR-AUTH-06: Admin user creation, deactivation, and password reset
 * - FR-AUTH-07 / BR-11: Temporary account lockout on repeated failed attempts
 */
public class AuthServiceTest {

    private static AuthService authService;
    private static UserDAO userDAO;

    @BeforeAll
    public static void setUpAll() {
        DatabaseInitializer.initializeDatabase();
        authService = new AuthService();
        userDAO = new UserDAO();
    }

    @BeforeEach
    public void setUp() {
        // Clear session before each test
        SessionManager.getInstance().logout();
    }

    @Test
    @DisplayName("FR-AUTH-01 & FR-AUTH-02: Successful login with valid BCrypt credentials")
    public void testSuccessfulLogin() throws AuthenticationException {
        User user = authService.login("admin", "Admin@123");

        assertNotNull(user, "User should not be null on successful login");
        assertEquals("admin", user.getUsername(), "Username should match");
        assertEquals(Role.Admin, user.getRole(), "Role should be Admin");
        assertTrue(user.getPasswordHash().startsWith("$2a$"), "Password should be hashed with BCrypt");
    }

    @Test
    @DisplayName("FR-AUTH-01: Login fails with invalid username")
    public void testLoginWithInvalidUsername() {
        AuthenticationException exception = assertThrows(
                AuthenticationException.class,
                () -> authService.login("non_existent_user_999", "password123")
        );

        assertTrue(exception.getMessage().contains("Invalid username or password"));
    }

    @Test
    @DisplayName("FR-AUTH-01: Login fails with incorrect password")
    public void testLoginWithIncorrectPassword() {
        AuthenticationException exception = assertThrows(
                AuthenticationException.class,
                () -> authService.login("admin", "WrongPassword!99")
        );

        assertTrue(exception.getMessage().contains("Invalid username or password"));
    }

    @Test
    @DisplayName("FR-AUTH-04: SessionManager maintains logged-in state across operations")
    public void testSessionManagerFlow() throws AuthenticationException {
        SessionManager session = SessionManager.getInstance();
        assertFalse(session.isLoggedIn(), "Session should be empty initially");

        User user = authService.login("admin", "Admin@123");
        session.setCurrentUser(user);

        assertTrue(session.isLoggedIn(), "Session should indicate user is logged in");
        assertEquals("admin", session.getCurrentUser().getUsername());
        assertEquals(Role.Admin, session.getCurrentRole());
        assertEquals(user.getFullName(), session.getCurrentUserDisplayName());

        session.logout();
        assertFalse(session.isLoggedIn(), "Session should be empty after logout");
        assertNull(session.getCurrentUser(), "Current user should be null after logout");
    }

    @Test
    @DisplayName("FR-AUTH-05: Role permissions and access check enforcement")
    public void testRolePermissions() {
        // Admin permissions
        assertTrue(authService.hasPermission(Role.Admin, Role.Admin));
        assertTrue(authService.hasPermission(Role.Admin, Role.Staff));
        assertTrue(authService.hasPermission(Role.Admin, Role.User));

        // Staff permissions
        assertFalse(authService.hasPermission(Role.Staff, Role.Admin));
        assertTrue(authService.hasPermission(Role.Staff, Role.Staff));
        assertTrue(authService.hasPermission(Role.Staff, Role.User));

        // User permissions
        assertFalse(authService.hasPermission(Role.User, Role.Admin));
        assertFalse(authService.hasPermission(Role.User, Role.Staff));
        assertTrue(authService.hasPermission(Role.User, Role.User));

        // SessionManager permission enforcement
        SessionManager session = SessionManager.getInstance();
        User normalUser = new User();
        normalUser.setUsername("testuser");
        normalUser.setRole(Role.User);
        session.setCurrentUser(normalUser);

        assertThrows(AccessDeniedException.class, () -> session.requireRole(Role.Admin));
        assertThrows(AccessDeniedException.class, () -> session.requireRole(Role.Staff));
        assertDoesNotThrow(() -> session.requireRole(Role.User));
    }

    @Test
    @DisplayName("FR-AUTH-06: Admin user creation, deactivation, and password reset")
    public void testAdminUserManagement() throws AuthenticationException {
        SessionManager.getInstance().setCurrentUser(authService.login("admin", "Admin@123"));
        String testUsername = "temp_user_" + System.currentTimeMillis();
        String testEmail = testUsername + "@example.com";
        String initialPassword = "Initial@Pass123";
        String newPassword = "NewSecret@Pass456";

        // 1. Admin creates user
        User createdUser = authService.createUser(
                testUsername,
                initialPassword,
                Role.User,
                "Temporary Test User",
                testEmail
        );

        assertNotNull(createdUser, "Created user should not be null");
        assertTrue(createdUser.getUserId() > 0, "Created user should have a valid generated ID");
        assertTrue(PasswordUtil.checkPassword(initialPassword, createdUser.getPasswordHash()));

        // Verify login works with initial password
        User loggedInUser = authService.login(testUsername, initialPassword);
        assertNotNull(loggedInUser);

        // 2. Admin resets password
        boolean resetSuccess = authService.resetPassword(createdUser.getUserId(), newPassword);
        assertTrue(resetSuccess, "Password reset should succeed");

        // Verify login with old password fails
        assertThrows(AuthenticationException.class, () -> authService.login(testUsername, initialPassword));

        // Verify login with new password succeeds
        User afterReset = authService.login(testUsername, newPassword);
        assertNotNull(afterReset);

        // 3. Admin deactivates user
        boolean deactivated = authService.deactivateUser(createdUser.getUserId());
        assertTrue(deactivated, "Deactivation should succeed");

        // Verify deactivated user cannot log in
        AuthenticationException deactException = assertThrows(
                AuthenticationException.class,
                () -> authService.login(testUsername, newPassword)
        );
        assertTrue(deactException.getMessage().contains("deactivated"));
    }

    @Test
    @DisplayName("FR-AUTH-07 / BR-11: Temporary lockout after 5 consecutive failed login attempts")
    public void testAccountLockoutAfterFiveFailedAttempts() throws AuthenticationException {
        SessionManager.getInstance().setCurrentUser(authService.login("admin", "Admin@123"));

        String lockTestUser = "lock_test_" + System.currentTimeMillis();
        String lockTestEmail = lockTestUser + "@example.com";
        String correctPass = "Correct@Pass123";

        // Create a test user for lockout testing
        User user = authService.createUser(
                lockTestUser,
                correctPass,
                Role.User,
                "Lockout Test User",
                lockTestEmail
        );

        // Attempt 1 to 4: Should fail with remaining attempts message
        for (int i = 1; i <= 4; i++) {
            final int attempt = i;
            AuthenticationException ex = assertThrows(
                    AuthenticationException.class,
                    () -> authService.login(lockTestUser, "WrongPassword" + attempt)
            );
            assertFalse(ex.isLocked(), "Account should not be locked yet at attempt " + attempt);
        }

        // Attempt 5: Should trigger account lockout
        AuthenticationException lockoutEx = assertThrows(
                AuthenticationException.class,
                () -> authService.login(lockTestUser, "WrongPassword5")
        );
        assertTrue(lockoutEx.isLocked(), "5th failed attempt should trigger lockout");
        assertTrue(lockoutEx.getMessage().contains("locked"));

        // Subsequent attempt with even the CORRECT password must be blocked during lockout
        AuthenticationException blockedEx = assertThrows(
                AuthenticationException.class,
                () -> authService.login(lockTestUser, correctPass)
        );
        assertTrue(blockedEx.isLocked(), "Correct password should still be blocked while account is locked");

        // Admin resets lockout/password so state is cleaned up
        authService.resetPassword(user.getUserId(), correctPass);
        assertDoesNotThrow(() -> authService.login(lockTestUser, correctPass));
    }
}
