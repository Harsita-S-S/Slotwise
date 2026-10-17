package com.fms.model;

import java.sql.Timestamp;

/**
 * Represents a user account in the SlotWise system.
 * Maps to the 'users' table in the database.
 *
 * Covers:
 * - FR-AUTH-01: Username and password fields for login.
 * - FR-AUTH-02: Password stored as a BCrypt hash (password_hash field).
 * - FR-AUTH-03: Role field determines which dashboard the user sees.
 * - FR-AUTH-05: Role field used for access control checks.
 * - FR-AUTH-07 / BR-11: failedAttempts and lockedUntil for account lockout.
 */
public class User {

    private int userId;
    private String username;
    private String passwordHash;
    private Role role;
    private String fullName;
    private String email;
    private boolean isActive;
    private int failedAttempts;
    private Timestamp lockedUntil;
    private Timestamp createdAt;

    /** Default constructor */
    public User() {
    }

    /**
     * Constructor for creating a new user (before saving to DB).
     * The userId and createdAt will be set by the database.
     */
    public User(String username, String passwordHash, Role role,
                String fullName, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.email = email;
        this.isActive = true;
        this.failedAttempts = 0;
        this.lockedUntil = null;
    }

    // --- Getters ---

    public int getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Timestamp getLockedUntil() {
        return lockedUntil;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    // --- Setters ---

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public void setFailedAttempts(int failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public void setLockedUntil(Timestamp lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", role=" + role +
                ", fullName='" + fullName + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}
