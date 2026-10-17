package com.fms;

import com.fms.exception.AccessDeniedException;
import com.fms.exception.InvalidInputException;
import com.fms.model.Facility;
import com.fms.model.FacilityStatus;
import com.fms.model.FacilityType;
import com.fms.model.Role;
import com.fms.model.User;
import com.fms.service.AuthService;
import com.fms.service.FacilityService;
import com.fms.service.SessionManager;
import com.fms.util.DBConnection;
import com.fms.util.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Facility Management (Module 2).
 * Covers:
 * - FR-FAC-01: Add facility with all required fields
 * - FR-FAC-02: Edit facility, deactivate, and reactivate
 * - FR-FAC-03: Facility status maintenance (Available, Under Maintenance, Inactive)
 * - FR-FAC-04: Search and filter by type, capacity, building, and amenity
 * - FR-FAC-05: Prevent duplicate facility names within the same building
 * - FR-FAC-06: Refuse deactivation if future bookings exist unless confirmed
 */
public class FacilityServiceTest {

    private static FacilityService facilityService;
    private static User adminUser;
    private static User regularUser;

    @BeforeAll
    public static void setUpAll() {
        DatabaseInitializer.initializeDatabase();
        facilityService = new FacilityService();

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
        // Set admin session by default for admin actions
        SessionManager.getInstance().setCurrentUser(adminUser);
    }

    @Test
    @DisplayName("FR-FAC-01: Admin adds a new facility with valid fields")
    public void testAdminAddFacility() {
        String name = "Test Hall " + System.currentTimeMillis();
        String building = "Engineering Block";

        Facility created = facilityService.addFacility(
                name,
                FacilityType.Hall,
                building,
                2,
                120,
                "Projector, Wi-Fi, AC",
                true
        );

        assertNotNull(created);
        assertTrue(created.getFacilityId() > 0);
        assertEquals(name, created.getName());
        assertEquals(FacilityType.Hall, created.getType());
        assertEquals(building, created.getBuilding());
        assertEquals(2, created.getFloor());
        assertEquals(120, created.getCapacity());
        assertEquals(FacilityStatus.Available, created.getStatus());
        assertTrue(created.isRequiresApproval());
    }

    @Test
    @DisplayName("FR-FAC-01: Non-admin users are blocked from adding facilities")
    public void testNonAdminCannotAddFacility() {
        SessionManager.getInstance().setCurrentUser(regularUser);

        assertThrows(AccessDeniedException.class, () ->
                facilityService.addFacility("Unauthorized Lab", FacilityType.Laboratory,
                        "Science Block", 1, 30, "Computers", false)
        );
    }

    @Test
    @DisplayName("FR-FAC-01: Invalid inputs throw InvalidInputException")
    public void testInvalidFacilityInputs() {
        // Empty name
        assertThrows(InvalidInputException.class, () ->
                facilityService.addFacility("", FacilityType.Classroom, "Block A", 1, 40, "", false)
        );

        // Zero/negative capacity
        assertThrows(InvalidInputException.class, () ->
                facilityService.addFacility("Room 101", FacilityType.Classroom, "Block A", 1, 0, "", false)
        );

        // Negative floor
        assertThrows(InvalidInputException.class, () ->
                facilityService.addFacility("Room 102", FacilityType.Classroom, "Block A", -1, 40, "", false)
        );
    }

    @Test
    @DisplayName("FR-FAC-05: System prevents duplicate facility names within the same building")
    public void testPreventDuplicateFacilityNameInSameBuilding() {
        String uniqueBuilding = "Unique Building " + System.currentTimeMillis();
        String facName = "Robotics Lab";

        // First creation succeeds
        facilityService.addFacility(facName, FacilityType.Laboratory, uniqueBuilding, 1, 40, "Robots", false);

        // Duplicate creation in same building must fail (FR-FAC-05)
        InvalidInputException ex = assertThrows(InvalidInputException.class, () ->
                facilityService.addFacility(facName, FacilityType.Laboratory, uniqueBuilding, 2, 50, "Robots", false)
        );

        assertTrue(ex.getMessage().contains("already exists in"));

        // Same facility name in a DIFFERENT building should succeed
        String diffBuilding = "Different Building " + System.currentTimeMillis();
        assertDoesNotThrow(() ->
                facilityService.addFacility(facName, FacilityType.Laboratory, diffBuilding, 1, 40, "Robots", false)
        );
    }

    @Test
    @DisplayName("FR-FAC-02 & FR-FAC-03: Admin edits details, changes status, deactivates and reactivates")
    public void testEditAndStatusTransitions() {
        String facName = "Status Test Lab " + System.currentTimeMillis();
        Facility fac = facilityService.addFacility(facName, FacilityType.Laboratory, "Lab Complex", 1, 30, "LAN", false);

        // 1. Edit facility details (FR-FAC-02)
        fac.setCapacity(45);
        fac.setAmenities("LAN, AC, 40 PCs");
        boolean updated = facilityService.updateFacility(fac);
        assertTrue(updated);

        Optional<Facility> fetched = facilityService.getFacilityById(fac.getFacilityId());
        assertTrue(fetched.isPresent());
        assertEquals(45, fetched.get().getCapacity());
        assertTrue(fetched.get().hasAmenity("AC"));

        // 2. Change status to Under Maintenance (FR-FAC-03)
        facilityService.changeFacilityStatus(fac.getFacilityId(), FacilityStatus.Under_Maintenance);
        assertEquals(FacilityStatus.Under_Maintenance,
                facilityService.getFacilityById(fac.getFacilityId()).get().getStatus());

        // 3. Deactivate facility (sets Inactive) (FR-FAC-02)
        FacilityService.DeactivationResult deactRes = facilityService.deactivateFacility(fac.getFacilityId(), false);
        assertTrue(deactRes.isSuccess());
        assertEquals(FacilityStatus.Inactive,
                facilityService.getFacilityById(fac.getFacilityId()).get().getStatus());

        // 4. Reactivate facility (sets Available) (FR-FAC-02)
        boolean reactivated = facilityService.reactivateFacility(fac.getFacilityId());
        assertTrue(reactivated);
        assertEquals(FacilityStatus.Available,
                facilityService.getFacilityById(fac.getFacilityId()).get().getStatus());
    }

    @Test
    @DisplayName("FR-FAC-04: All users can search and filter facilities by type, capacity, building, and amenity")
    public void testSearchAndFilterFacilities() {
        // Switch to regular user session
        SessionManager.getInstance().setCurrentUser(regularUser);

        // Filter by type: Classroom
        List<Facility> classrooms = facilityService.searchFacilities(null, FacilityType.Classroom, null, null, null, null);
        assertNotNull(classrooms);
        for (int i = 0; i < classrooms.size(); i++) {
            assertEquals(FacilityType.Classroom, classrooms.get(i).getType());
        }

        // Filter by capacity >= 50
        List<Facility> large = facilityService.searchFacilities(null, null, null, 50, null, null);
        for (int i = 0; i < large.size(); i++) {
            assertTrue(large.get(i).getCapacity() >= 50);
        }

        // Filter by amenity: Projector
        List<Facility> withProjector = facilityService.searchFacilities(null, null, null, null, "Projector", null);
        for (int i = 0; i < withProjector.size(); i++) {
            assertTrue(withProjector.get(i).hasAmenity("Projector"));
        }
    }

    @Test
    @DisplayName("FR-FAC-06: Refuse deactivation if future approved bookings exist unless confirmed")
    public void testDeactivateFacilityWithFutureBookings() throws SQLException {
        // Create test facility
        String facName = "Booking Protect Lab " + System.currentTimeMillis();
        Facility fac = facilityService.addFacility(facName, FacilityType.Laboratory, "Main Block", 1, 50, "LAN", false);
        int facId = fac.getFacilityId();

        // Insert a future approved booking into the database
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String insertBookingSql = "INSERT INTO bookings (facility_id, user_id, booking_date, slot_no, purpose, attendees, status, active_flag) " +
                                  "VALUES (?, ?, ?, 1, 'Research Testing', 20, 'Approved', 1)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertBookingSql)) {
            pstmt.setInt(1, facId);
            pstmt.setInt(2, regularUser.getUserId());
            pstmt.setDate(3, Date.valueOf(tomorrow));
            pstmt.executeUpdate();
        }

        // Attempt 1: Deactivate without confirmation -> Must be refused! (FR-FAC-06)
        FacilityService.DeactivationResult refused = facilityService.deactivateFacility(facId, false);
        assertFalse(refused.isSuccess(), "Deactivation should be refused when future bookings exist");
        assertTrue(refused.isConfirmationRequired(), "Confirmation should be flagged as required");
        assertTrue(refused.getFutureBookingsCount() >= 1, "Should count future approved bookings");

        // Facility status must remain unchanged (Available)
        assertEquals(FacilityStatus.Available, facilityService.getFacilityById(facId).get().getStatus());

        // Attempt 2: Deactivate WITH confirmation -> Must succeed, cancel bookings, and notify (FR-FAC-06)
        FacilityService.DeactivationResult confirmed = facilityService.deactivateFacility(facId, true);
        assertTrue(confirmed.isSuccess(), "Deactivation should succeed when Admin confirms");
        assertEquals(FacilityStatus.Inactive, facilityService.getFacilityById(facId).get().getStatus());

        // Verify future booking was cancelled
        assertEquals(0, facilityService.getFutureApprovedBookingsCount(facId),
                "Future approved bookings should now be cancelled");
    }
}
