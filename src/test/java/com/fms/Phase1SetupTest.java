package com.fms;

import com.fms.model.BookingStatus;
import com.fms.model.Role;
import com.fms.util.DBConnection;
import com.fms.util.DatabaseInitializer;
import com.fms.util.PasswordUtil;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 1 Setup Acceptance and Verification Test Suite.
 * Validates:
 * - Connection pooling and database accessibility (NFR-04, NFR-09)
 * - Schema tables, dependencies, and reference data
 * - BCrypt password hashing and lockout evaluation (FR-AUTH-02, BR-11)
 * - Composite unique constraint for booking conflict prevention (BR-01, Sec 7.3)
 * - Transaction rollback behavior (FR-VAL-03, BR-03)
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class Phase1SetupTest {

    @BeforeAll
    public static void setUp() {
        // Run database initialization and seeding
        DatabaseInitializer.initializeDatabase();
    }

    @Test
    @Order(1)
    @DisplayName("Verify HikariCP Connection Pool and Database Connectivity")
    public void testDatabaseConnection() {
        DBConnection db = DBConnection.getInstance();
        assertNotNull(db, "DBConnection singleton instance should not be null");
        assertTrue(db.testConnection(), "Database connection test should succeed");
    }

    @Test
    @Order(2)
    @DisplayName("Verify Fixed Time Slots (10 slots between 08:00 and 18:00)")
    public void testFixedTimeSlots() throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM time_slots")) {
            assertTrue(rs.next());
            assertEquals(10, rs.getInt(1), "System must contain exactly 10 fixed time slots");
        }
    }

    @Test
    @Order(3)
    @DisplayName("Verify Seeded Facilities and Types")
    public void testFacilitiesSeeded() throws SQLException {
        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM facilities")) {
            assertTrue(rs.next());
            assertTrue(rs.getInt(1) >= 3, "At least 3 facilities (Classroom, Lab, Hall) must be seeded");
        }
    }

    @Test
    @Order(4)
    @DisplayName("Verify BCrypt Password Hashing and Authentication Verification")
    public void testBCryptPasswordUtility() throws SQLException {
        String rawPassword = "Admin@123";
        String hashed = PasswordUtil.hashPassword(rawPassword);
        
        assertNotNull(hashed);
        assertTrue(hashed.startsWith("$2a$") || hashed.startsWith("$2b$"), "Must be a valid BCrypt hash format");
        assertTrue(PasswordUtil.checkPassword(rawPassword, hashed), "Plaintext must verify against generated hash");
        assertFalse(PasswordUtil.checkPassword("WrongPass", hashed), "Invalid password must be rejected");

        // Verify seeded admin user credentials in DB
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement("SELECT password_hash FROM users WHERE username = ?")) {
            pstmt.setString(1, "admin");
            try (ResultSet rs = pstmt.executeQuery()) {
                assertTrue(rs.next(), "Admin user must exist in DB");
                String dbHash = rs.getString("password_hash");
                assertTrue(PasswordUtil.checkPassword("Admin@123", dbHash), 
                    "Stored database password hash for 'admin' must match 'Admin@123'");
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("Verify Account Lockout Rule (BR-11: 5 minutes after 5 failed attempts)")
    public void testAccountLockoutRule() {
        Timestamp futureLock = PasswordUtil.calculateLockoutTime(5);
        assertTrue(PasswordUtil.isAccountLocked(futureLock), "Future locked_until must indicate account is locked");
        assertFalse(PasswordUtil.isAccountLocked(null), "Null locked_until must indicate account is active");
    }

    @Test
    @Order(6)
    @DisplayName("Verify BR-01 Composite Unique Constraint on Bookings (facility, date, slot, active_flag)")
    public void testDoubleBookingUniqueConstraint() throws SQLException {
        Date testDate = Date.valueOf("2026-11-20");
        int testFacilityId = 1;
        int testSlotNo = 2;
        int testUserId = 1;

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            // Clean up any lingering test records
            try (PreparedStatement cleanStmt = conn.prepareStatement(
                    "DELETE FROM bookings WHERE booking_date = ? AND slot_no = ?")) {
                cleanStmt.setDate(1, testDate);
                cleanStmt.setInt(2, testSlotNo);
                cleanStmt.executeUpdate();
            }

            // Insert initial active booking (active_flag = 1)
            String insertSql = "INSERT INTO bookings (facility_id, user_id, booking_date, slot_no, purpose, attendees, status, active_flag) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, 1)";
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setInt(1, testFacilityId);
                insertStmt.setInt(2, testUserId);
                insertStmt.setDate(3, testDate);
                insertStmt.setInt(4, testSlotNo);
                insertStmt.setString(5, "Initial Valid Booking");
                insertStmt.setInt(6, 25);
                insertStmt.setString(7, BookingStatus.Approved.name());
                insertStmt.executeUpdate();
            }

            // Attempt duplicate active booking for the exact same facility, date, and slot
            SQLException duplicateException = assertThrows(SQLException.class, () -> {
                try (PreparedStatement dupStmt = conn.prepareStatement(insertSql)) {
                    dupStmt.setInt(1, testFacilityId);
                    dupStmt.setInt(2, testUserId);
                    dupStmt.setDate(3, testDate);
                    dupStmt.setInt(4, testSlotNo);
                    dupStmt.setString(5, "Clashing Duplicate Booking");
                    dupStmt.setInt(6, 30);
                    dupStmt.setString(7, BookingStatus.Approved.name());
                    dupStmt.executeUpdate();
                }
            }, "Database must reject clashing active booking via unique constraint (BR-01)");

            assertTrue(duplicateException.getMessage().toLowerCase().contains("duplicate"), 
                "Exception must be a duplicate entry error");

            // Cancel the first booking by setting active_flag = NULL
            try (PreparedStatement cancelStmt = conn.prepareStatement(
                    "UPDATE bookings SET status = 'Cancelled', active_flag = NULL WHERE booking_date = ? AND slot_no = ?")) {
                cancelStmt.setDate(1, testDate);
                cancelStmt.setInt(2, testSlotNo);
                cancelStmt.executeUpdate();
            }

            // Verify that after cancellation (active_flag = NULL), a new booking for the same slot SUCCEEDS
            assertDoesNotThrow(() -> {
                try (PreparedStatement rebookStmt = conn.prepareStatement(insertSql)) {
                    rebookStmt.setInt(1, testFacilityId);
                    rebookStmt.setInt(2, testUserId);
                    rebookStmt.setDate(3, testDate);
                    rebookStmt.setInt(4, testSlotNo);
                    rebookStmt.setString(5, "Re-booked freed slot");
                    rebookStmt.setInt(6, 20);
                    rebookStmt.setString(7, BookingStatus.Approved.name());
                    rebookStmt.executeUpdate();
                }
            }, "Rebooking a cancelled slot must succeed as active_flag of cancelled row is NULL");

            // Clean up test rows
            try (PreparedStatement cleanStmt = conn.prepareStatement(
                    "DELETE FROM bookings WHERE booking_date = ? AND slot_no = ?")) {
                cleanStmt.setDate(1, testDate);
                cleanStmt.setInt(2, testSlotNo);
                cleanStmt.executeUpdate();
            }
        }
    }

    @Test
    @Order(7)
    @DisplayName("Verify Transaction Atomic Rollback (FR-VAL-03, BR-03)")
    public void testTransactionRollback() {
        Date txDate = Date.valueOf("2026-11-25");
        
        assertThrows(RuntimeException.class, () -> {
            DBConnection.executeInTransaction(conn -> {
                String insertSql = "INSERT INTO bookings (facility_id, user_id, booking_date, slot_no, purpose, attendees, status, active_flag) " +
                                   "VALUES (1, 1, ?, 3, 'Tx Test 1', 10, 'Approved', 1)";
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setDate(1, txDate);
                    stmt.executeUpdate();
                }

                // Simulate business rule failure / exception mid-transaction
                throw new RuntimeException("Simulated mid-transaction failure for rollback verification");
            });
        });

        // Verify that the record was completely rolled back and is NOT in the database
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(
                     "SELECT COUNT(*) FROM bookings WHERE booking_date = ? AND slot_no = 3")) {
            checkStmt.setDate(1, txDate);
            try (ResultSet rs = checkStmt.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(0, rs.getInt(1), "Rolled-back transaction record must not exist in database");
            }
        } catch (SQLException e) {
            fail("Database check failed: " + e.getMessage());
        }
    }
}
