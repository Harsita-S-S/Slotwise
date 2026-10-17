package com.fms.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility class for secure password hashing and account lockout evaluation.
 * Fulfills:
 * - FR-AUTH-02: Passwords stored as salted BCrypt hashes, never in plain text.
 * - BR-11: Salted password hashing and 5-minute lockout rules.
 * - NFR-04: Security best practices.
 */
public class PasswordUtil {

    private static final int BCRYPT_LOG_ROUNDS = 12;

    private PasswordUtil() {
        // Prevent instantiation
    }

    /**
     * Hashes a plain-text password using BCrypt with a randomly generated salt.
     *
     * @param plainPassword The plain text password
     * @return The 60-character BCrypt salted hash string
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     *
     * @param plainPassword  The candidate plain text password
     * @param hashedPassword The stored salted BCrypt hash
     * @return True if match, false otherwise
     */
    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null || hashedPassword.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            // Catches malformed hashes safely
            return false;
        }
    }

    /**
     * Checks if an account is currently locked based on its locked_until timestamp.
     *
     * @param lockedUntil The timestamp until which account is locked, or null
     * @return True if lockedUntil is in the future; false otherwise
     */
    public static boolean isAccountLocked(Timestamp lockedUntil) {
        if (lockedUntil == null) {
            return false;
        }
        return lockedUntil.after(Timestamp.from(Instant.now()));
    }

    /**
     * Calculates the timestamp for a temporary account lockout.
     *
     * @param lockoutMinutes Number of minutes to lock (typically 5 as per BR-11)
     * @return Timestamp representing current time + lockoutMinutes
     */
    public static Timestamp calculateLockoutTime(int lockoutMinutes) {
        return Timestamp.from(Instant.now().plus(lockoutMinutes, ChronoUnit.MINUTES));
    }
}
