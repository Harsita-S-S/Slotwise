package com.fms;

import com.fms.exception.AccessDeniedException;
import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import com.fms.service.AuthService;
import com.fms.service.SessionManager;
import com.fms.service.StaffService;
import com.fms.util.DBConnection;
import com.fms.exception.AuthenticationException;
import com.fms.util.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Staff Management (Module 4).
 * Covers:
 * - FR-STF-01: Admin adds, edits, deactivates, and reactivates staff records with linked login accounts
 * - FR-STF-02: Specializations include Electrician, Lab Technician, Plumber, IT Support, General
 * - FR-STF-03: Workload tracking and open tasks inspection
 */
public class StaffServiceTest {

    private static StaffService staffService;
    private static AuthService authService;
    private static User adminUser;
    private static User regularUser;

    @BeforeAll
    public static void setUpAll() {
        DatabaseInitializer.initializeDatabase();
        staffService = new StaffService();
        authService = new AuthService();

        adminUser = new User();
        adminUser.setUserId(1);
        adminUser.setUsername("admin");
        adminUser.setRole(Role.Admin);
        adminUser.setFullName("Administrator");

        regularUser = new User();
        regularUser.setUserId(2);
        regularUser.setUsername("harsita");
        regularUser.setRole(Role.User);
        regularUser.setFullName("Harsita");
    }

    @BeforeEach
    public void setUp() {
        SessionManager.getInstance().setCurrentUser(adminUser);
    }

    @Test
    @DisplayName("FR-STF-01 & FR-STF-02: Admin adds staff record with trade specialization and linked account")
    public void testAdminAddStaffSuccess() {
        // Create a new user with role Staff
        String username = "plumber_ramesh_" + System.currentTimeMillis();
        User user = authService.createUser(username, "Staff@123", Role.Staff,
                "Ramesh Patel", username + "@fms.edu");

        Staff created = staffService.addStaff(user.getUserId(), StaffSpecialization.Plumber, "+91 9123456789");

        assertNotNull(created);
        assertTrue(created.getStaffId() > 0);
        assertEquals(user.getUserId(), created.getUserId());
        assertEquals(StaffSpecialization.Plumber, created.getSpecialization());
        assertEquals("+91 9123456789", created.getPhone());
        assertTrue(created.isActive());
        assertEquals("Ramesh Patel", created.getFullName());
    }

    @Test
    @DisplayName("FR-STF-01: Non-admin users are blocked from adding or editing staff")
    public void testNonAdminCannotManageStaff() {
        SessionManager.getInstance().setCurrentUser(regularUser);

        assertThrows(AccessDeniedException.class, () ->
                staffService.addStaff(1, StaffSpecialization.Electrician, "1234567890")
        );
    }

    @Test
    @DisplayName("FR-STF-01: Linking non-Staff user accounts or duplicate links is rejected")
    public void testStaffAccountLinkValidation() {
        // Create user with role User (not Staff)
        String username = "student_test_" + System.currentTimeMillis();
        User nonStaff = authService.createUser(username, "User@123", Role.User,
                "Student Tester", username + "@fms.edu");

        // Attempt to link a regular user as Staff -> Must fail!
        InvalidInputException ex = assertThrows(InvalidInputException.class, () ->
                staffService.addStaff(nonStaff.getUserId(), StaffSpecialization.General, "9876543210")
        );
        assertTrue(ex.getMessage().contains("Only Staff role accounts can be linked"));

        // Create a Staff user and link them once
        String staffName = "staff_dup_" + System.currentTimeMillis();
        User staffUser = authService.createUser(staffName, "Staff@123", Role.Staff,
                "Dup Tester", staffName + "@fms.edu");

        staffService.addStaff(staffUser.getUserId(), StaffSpecialization.ITSupport, "9876543211");

        // Attempting to link the same user account a second time -> Must fail!
        assertThrows(InvalidInputException.class, () ->
                staffService.addStaff(staffUser.getUserId(), StaffSpecialization.General, "9876543212")
        );
    }

    @Test
    @DisplayName("FR-STF-01: Admin edits details, deactivates, and reactivates staff members")
    public void testEditAndDeactivateStaff() {
        String staffName = "staff_lifecycle_" + System.currentTimeMillis();
        User staffUser = authService.createUser(staffName, "Staff@123", Role.Staff,
                "Lifecycle Staff", staffName + "@fms.edu");

        Staff staff = staffService.addStaff(staffUser.getUserId(), StaffSpecialization.Electrician, "9876500000");
        int staffId = staff.getStaffId();

        // 1. Edit specialization and phone
        boolean updated = staffService.updateStaff(staffId, StaffSpecialization.ITSupport, "9876599999", true);
        assertTrue(updated);

        Optional<Staff> fetched = staffService.getStaffById(staffId);
        assertTrue(fetched.isPresent());
        assertEquals(StaffSpecialization.ITSupport, fetched.get().getSpecialization());
        assertEquals("9876599999", fetched.get().getPhone());

        // 2. Deactivate staff member
        boolean deactivated = staffService.deactivateStaff(staffId);
        assertTrue(deactivated);
        assertFalse(staffService.getStaffById(staffId).get().isActive());

        // 3. Reactivate staff member
        boolean reactivated = staffService.reactivateStaff(staffId);
        assertTrue(reactivated);
        assertTrue(staffService.getStaffById(staffId).get().isActive());
    }

    @Test
    @DisplayName("FR-STF-03: Displays live workload metrics and open tasks for staff members")
    public void testWorkloadAndOpenTasksTracking() throws SQLException {
        String staffName = "staff_workload_" + System.currentTimeMillis();
        User staffUser = authService.createUser(staffName, "Staff@123", Role.Staff,
                "Workload Tech", staffName + "@fms.edu");

        Staff staff = staffService.addStaff(staffUser.getUserId(), StaffSpecialization.Electrician, "9876511111");
        int staffId = staff.getStaffId();

        // Initially 0 open tasks
        assertEquals(0, staffService.getStaffById(staffId).get().getOpenTaskCount());

        // Assign 2 maintenance requests to this staff member
        String insertTaskSql = "INSERT INTO maintenance_requests (facility_id, raised_by, assigned_staff, description, priority, status) " +
                               "VALUES (1, 1, ?, 'Replace burnt socket wiring in Hall 101', 'High', 'Assigned')";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertTaskSql)) {
            pstmt.setInt(1, staffId);
            pstmt.executeUpdate();
            pstmt.executeUpdate(); // 2nd task
        }

        // Check updated workload count (FR-STF-03)
        Staff updatedStaff = staffService.getStaffById(staffId).get();
        assertEquals(2, updatedStaff.getOpenTaskCount());
        // Workload label rendering is handled by StaffController.createWorkloadBadge() - not the model

        // Retrieve open tasks list
        List<StaffTaskSummary> openTasks = staffService.getStaffOpenTasks(staffId);
        assertEquals(2, openTasks.size());
        assertEquals("Replace burnt socket wiring in Hall 101", openTasks.get(0).getDescription());
        assertEquals(Priority.High, openTasks.get(0).getPriority());
    }
}
