package com.fms;

import com.fms.dao.BookingDAO;
import com.fms.dao.EquipmentDAO;
import com.fms.dao.MaintenanceDAO;
import com.fms.exception.AccessDeniedException;
import com.fms.exception.BookingConflictException;
import com.fms.exception.InvalidInputException;
import com.fms.exception.InvalidStatusTransitionException;
import com.fms.model.*;
import com.fms.service.*;
import com.fms.util.DBConnection;
import com.fms.util.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Integration tests for Module 6 (SRS 4.6): requests, assignment, status flow, auto-blocking. */
public class MaintenanceServiceTest {

    private static final String PW = "Test@12345";

    private static AuthService authService;
    private static MaintenanceService maintenanceService;
    private static BookingService bookingService;
    private static StaffService staffService;

    private static User admin;
    private static User reporter;
    private static User otherUser;
    private static User techAUser;
    private static User techBUser;
    private static Staff techA;
    private static Staff techB;
    private static Staff techC;
    private static Facility facility;
    private static Facility otherFacility;
    private static Equipment equipment;

    @BeforeAll
    public static void setUpAll() throws Exception {
        DatabaseInitializer.initializeDatabase();
        authService = new AuthService();
        staffService = new StaffService();
        maintenanceService = new MaintenanceService();
        bookingService = new BookingService();
        FacilityService facilityService = new FacilityService();
        EquipmentService equipmentService = new EquipmentService();

        admin = authService.login("admin", "Admin@123");
        SessionManager.getInstance().setCurrentUser(admin);

        long ts = System.currentTimeMillis();
        reporter = authService.createUser("rep_" + ts, PW, Role.User, "Reporter " + ts, "rep_" + ts + "@fms.edu");
        otherUser = authService.createUser("oth_" + ts, PW, Role.User, "Other " + ts, "oth_" + ts + "@fms.edu");

        techA = staffService.addStaffWithNewAccount("ta_" + ts, PW, "Tech A " + ts, "ta_" + ts + "@fms.edu",
                StaffSpecialization.Electrician, "+91 9000000011");
        techB = staffService.addStaffWithNewAccount("tb_" + ts, PW, "Tech B " + ts, "tb_" + ts + "@fms.edu",
                StaffSpecialization.Plumber, "+91 9000000012");
        techC = staffService.addStaffWithNewAccount("tc_" + ts, PW, "Tech C " + ts, "tc_" + ts + "@fms.edu",
                StaffSpecialization.General, "+91 9000000013");
        techAUser = authService.login("ta_" + ts, PW);
        techBUser = authService.login("tb_" + ts, PW);

        facility = facilityService.addFacility("Maint Lab " + ts, FacilityType.Classroom, "Maint Block", 1, 30,
                "Tools", false);
        otherFacility = facilityService.addFacility("Other Lab " + ts, FacilityType.Classroom, "Maint Block", 2, 30,
                "Tools", false);
        equipment = equipmentService.addEquipment(facility.getFacilityId(), "Test Rig " + ts, "Tools",
                "SN-MNT-" + ts, Date.valueOf(LocalDate.now().minusDays(10)));
    }

    private static void loginAs(User u) {
        SessionManager.getInstance().setCurrentUser(u);
    }

    private static MaintenanceRequest newRequest(Facility f, Integer equipmentId, Priority p) {
        MaintenanceRequest r = new MaintenanceRequest();
        r.setFacilityId(f.getFacilityId());
        r.setEquipmentId(equipmentId);
        r.setDescription("Something is broken in " + f.getName());
        r.setPriority(p);
        return r;
    }

    private static MaintenanceRequest raise(Facility f, Integer equipmentId) throws Exception {
        loginAs(reporter);
        return maintenanceService.createRequest(newRequest(f, equipmentId, Priority.High));
    }

    private static MaintenanceRequest assign(MaintenanceRequest r, Staff tech, LocalDate date, int s, int e) throws Exception {
        loginAs(admin);
        maintenanceService.assignRequest(r.getRequestId(), tech.getStaffId(), date, s, e, null);
        return new MaintenanceDAO().findById(r.getRequestId()).orElseThrow();
    }

    private static int logCount(int requestId) throws Exception {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM maintenance_log WHERE request_id = ?")) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("FR-MNT-01: any logged-in user raises a request; it starts as Reported and is visible only to its owner")
    public void testRaiseRequestAndVisibility() throws Exception {
        MaintenanceRequest r = raise(facility, equipment.getEquipmentId());
        assertTrue(r.getRequestId() > 0);
        assertEquals(MaintenanceStatus.Reported, r.getStatus());
        assertEquals(1, logCount(r.getRequestId()));

        loginAs(reporter);
        assertTrue(maintenanceService.getVisibleRequests().stream().anyMatch(x -> x.getRequestId() == r.getRequestId()));
        loginAs(otherUser);
        assertTrue(maintenanceService.getVisibleRequests().stream().noneMatch(x -> x.getRequestId() == r.getRequestId()));
    }

    @Test
    @DisplayName("FR-MNT-01: description is required and equipment must belong to the chosen facility")
    public void testRaiseRequestValidation() {
        loginAs(reporter);
        MaintenanceRequest blank = newRequest(facility, null, Priority.Low);
        blank.setDescription("   ");
        assertThrows(InvalidInputException.class, () -> maintenanceService.createRequest(blank));

        MaintenanceRequest mismatch = newRequest(otherFacility, equipment.getEquipmentId(), Priority.Low);
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> maintenanceService.createRequest(mismatch));
        assertTrue(ex.getMessage().contains("does not belong"), "message was: " + ex.getMessage());
    }

    @Test
    @DisplayName("FR-MNT-02/BR-10: only Admin assigns, and only to an active Staff member")
    public void testAssignRules() throws Exception {
        MaintenanceRequest r = raise(facility, null);
        LocalDate day = LocalDate.now().plusDays(3);

        loginAs(reporter);
        assertThrows(AccessDeniedException.class,
                () -> maintenanceService.assignRequest(r.getRequestId(), techA.getStaffId(), day, 2, 3, null));

        loginAs(admin);
        staffService.deactivateStaff(techC.getStaffId());
        assertThrows(InvalidInputException.class,
                () -> maintenanceService.assignRequest(r.getRequestId(), techC.getStaffId(), day, 2, 3, null));
        assertThrows(InvalidInputException.class,
                () -> maintenanceService.assignRequest(r.getRequestId(), techA.getStaffId(), LocalDate.now().minusDays(1), 2, 3, null));
        assertThrows(InvalidInputException.class,
                () -> maintenanceService.assignRequest(r.getRequestId(), techA.getStaffId(), day, 5, 3, null));

        MaintenanceRequest assigned = assign(r, techA, day, 2, 3);
        assertEquals(MaintenanceStatus.Assigned, assigned.getStatus());
        assertEquals(techA.getStaffId(), assigned.getAssignedStaffId());
        assertEquals(2, logCount(r.getRequestId()));
    }

    @Test
    @DisplayName("BR-09: status only moves forward one step; skipping or going back is an InvalidStatusTransitionException")
    public void testForwardOnlyFlow() throws Exception {
        MaintenanceRequest r = raise(facility, null);
        loginAs(admin);
        assertThrows(InvalidStatusTransitionException.class,
                () -> maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.Completed, "done"));
        assertThrows(InvalidStatusTransitionException.class,
                () -> maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.InProgress, "go"));

        assign(r, techA, LocalDate.now().plusDays(4), 1, 2);
        loginAs(techAUser);
        assertThrows(InvalidInputException.class,
                () -> maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.Completed, " "));
        assertThrows(InvalidStatusTransitionException.class,
                () -> maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.Completed, "skipped a step"));

        maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.InProgress, "Started");
        maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.Completed, "Replaced the part");
        MaintenanceRequest done = new MaintenanceDAO().findById(r.getRequestId()).orElseThrow();
        assertEquals(MaintenanceStatus.Completed, done.getStatus());
        assertNotNull(done.getCompletedAt());
        assertEquals(4, logCount(r.getRequestId()));

        // FR-MNT-03/05: every change is readable with who, when and the note
        List<MaintenanceLogEntry> history = new MaintenanceDAO().findLog(r.getRequestId());
        assertEquals(List.of("Reported", "Assigned", "In Progress", "Completed"),
                history.stream().map(MaintenanceLogEntry::getNewStatus).toList());
        MaintenanceLogEntry last = history.get(3);
        assertEquals("In Progress", last.getOldStatus());
        assertEquals("Replaced the part", last.getNote());
        assertEquals(techAUser.getUserId(), last.getChangedBy());
        assertNotNull(last.getChangedAt());
        assertEquals(techAUser.getFullName(), last.getChangedByName());

        assertThrows(InvalidStatusTransitionException.class,
                () -> maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.InProgress, "back again"));
    }

    @Test
    @DisplayName("FR-MNT-04: only the assigned Staff member can update a task")
    public void testOnlyAssignedStaffCanUpdate() throws Exception {
        MaintenanceRequest r = raise(facility, null);
        assign(r, techA, LocalDate.now().plusDays(5), 1, 1);

        loginAs(techBUser);
        assertThrows(AccessDeniedException.class,
                () -> maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.InProgress, null));
        loginAs(reporter);
        assertThrows(AccessDeniedException.class,
                () -> maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.InProgress, null));

        loginAs(techAUser);
        List<MaintenanceRequest> mine = maintenanceService.getVisibleRequests();
        assertTrue(mine.stream().anyMatch(x -> x.getRequestId() == r.getRequestId()));
        loginAs(techBUser);
        assertTrue(maintenanceService.getVisibleRequests().stream().noneMatch(x -> x.getRequestId() == r.getRequestId()));
    }

    @Test
    @DisplayName("FR-X1-01/03: scheduled maintenance blocks booking of its slots and completion releases them")
    public void testAutoBlockingAndRelease() throws Exception {
        LocalDate day = LocalDate.now().plusDays(6);
        MaintenanceRequest r = raise(facility, null);
        assign(r, techA, day, 4, 5);

        loginAs(reporter);
        assertThrows(BookingConflictException.class,
                () -> bookingService.createBooking(facility.getFacilityId(), day, List.of(4), "Blocked", 5));
        assertThrows(BookingConflictException.class,
                () -> bookingService.createBooking(facility.getFacilityId(), day, List.of(5), "Blocked", 5));
        assertEquals(1, bookingService.createBooking(facility.getFacilityId(), day, List.of(6), "Outside window", 5).size());

        loginAs(techAUser);
        maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.InProgress, null);
        maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.Completed, "All fixed");

        loginAs(reporter);
        assertEquals(1, bookingService.createBooking(facility.getFacilityId(), day, List.of(4), "Released", 5).size());
    }

    // ----------------------------------------------------------------------
    // Step 3: rules, side effects and notifications
    // ----------------------------------------------------------------------

    private static int notificationCount(int userId, String type, String refKeyPrefix) throws Exception {
        try (Connection c = DBConnection.getInstance().getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND type = ? AND ref_key LIKE ?")) {
            ps.setInt(1, userId);
            ps.setString(2, type);
            ps.setString(3, refKeyPrefix + "%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static boolean isAffected(Booking b) {
        return new BookingDAO().findById(b.getBookingId()).orElseThrow().isAffectedByMaintenance();
    }

    private static EquipmentCondition condition(Equipment e) {
        return new EquipmentDAO().findById(e.getEquipmentId()).orElseThrow().getConditionStatus();
    }

    @Test
    @DisplayName("BR-07/FR-X1-02: bookings inside a new window are flagged Affected and notified; completion clears the flag")
    public void testAffectedBookingsFlaggedNotifiedAndReleased() throws Exception {
        LocalDate day = LocalDate.now().plusDays(8);
        loginAs(reporter);
        Booking inside = bookingService.createBooking(facility.getFacilityId(), day, List.of(3), "Inside window", 5).get(0);
        Booking outside = bookingService.createBooking(facility.getFacilityId(), day, List.of(8), "Outside window", 5).get(0);
        assertFalse(isAffected(inside));

        MaintenanceRequest r = raise(facility, null);
        assign(r, techA, day, 2, 4);

        assertTrue(isAffected(inside));
        assertFalse(isAffected(outside));
        assertEquals(1, notificationCount(reporter.getUserId(), "BOOKING_AFFECTED",
                "BOOKING-AFFECTED-" + inside.getBookingId() + "-"));
        assertEquals(0, notificationCount(reporter.getUserId(), "BOOKING_AFFECTED",
                "BOOKING-AFFECTED-" + outside.getBookingId() + "-"));
        assertEquals(1, notificationCount(techAUser.getUserId(), "MAINTENANCE_ASSIGNED",
                "MAINT-ASSIGNED-" + r.getRequestId() + "-"));

        loginAs(techAUser);
        maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.InProgress, null);
        assertTrue(isAffected(inside), "still flagged while the work is in progress");
        maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.Completed, "Done");

        assertFalse(isAffected(inside));
        assertEquals(1, notificationCount(reporter.getUserId(), "MAINTENANCE_COMPLETED",
                "MAINT-COMPLETED-" + r.getRequestId()));
    }

    @Test
    @DisplayName("Re-assigning an Assigned request moves the block and the Affected flags; In Progress cannot be re-assigned")
    public void testReassignMovesWindow() throws Exception {
        LocalDate day1 = LocalDate.now().plusDays(9);
        LocalDate day2 = LocalDate.now().plusDays(10);
        loginAs(reporter);
        Booking b1 = bookingService.createBooking(facility.getFacilityId(), day1, List.of(3), "Day one", 5).get(0);
        Booking b2 = bookingService.createBooking(facility.getFacilityId(), day2, List.of(3), "Day two", 5).get(0);

        MaintenanceRequest r = raise(facility, null);
        assign(r, techA, day1, 3, 3);
        assertTrue(isAffected(b1));
        assertFalse(isAffected(b2));

        MaintenanceRequest moved = assign(r, techB, day2, 3, 3);
        assertEquals(MaintenanceStatus.Assigned, moved.getStatus());
        assertEquals(techB.getStaffId(), moved.getAssignedStaffId());
        assertFalse(isAffected(b1));
        assertTrue(isAffected(b2));
        assertFalse(new BookingDAO().isSlotUnderMaintenance(facility.getFacilityId(), day1, 3));
        assertTrue(new BookingDAO().isSlotUnderMaintenance(facility.getFacilityId(), day2, 3));
        assertEquals(1, notificationCount(techBUser.getUserId(), "MAINTENANCE_ASSIGNED",
                "MAINT-ASSIGNED-" + r.getRequestId() + "-"));

        loginAs(techBUser);
        maintenanceService.updateStatus(r.getRequestId(), MaintenanceStatus.InProgress, null);
        loginAs(admin);
        assertThrows(InvalidStatusTransitionException.class,
                () -> maintenanceService.assignRequest(r.getRequestId(), techA.getStaffId(), day1, 1, 1, null));
    }

    @Test
    @DisplayName("FR-MNT-06: equipment condition follows the work and Retired items are left alone")
    public void testEquipmentConditionFollowsWorkflow() throws Exception {
        loginAs(admin);
        long ts = System.currentTimeMillis();
        EquipmentService equipmentService = new EquipmentService();
        Equipment working = equipmentService.addEquipment(facility.getFacilityId(), "Cond Rig " + ts, "Tools",
                "SN-COND-" + ts, Date.valueOf(LocalDate.now().minusDays(5)));
        Equipment retired = equipmentService.addEquipment(facility.getFacilityId(), "Old Rig " + ts, "Tools",
                "SN-OLD-" + ts, Date.valueOf(LocalDate.now().minusDays(5)));
        equipmentService.updateCondition(retired.getEquipmentId(), EquipmentCondition.Retired);

        MaintenanceRequest r1 = raise(facility, working.getEquipmentId());
        MaintenanceRequest r2 = raise(facility, retired.getEquipmentId());
        assign(r1, techA, LocalDate.now().plusDays(11), 1, 1);
        assign(r2, techA, LocalDate.now().plusDays(12), 1, 1);
        assertEquals(EquipmentCondition.Good, condition(working));

        loginAs(techAUser);
        maintenanceService.updateStatus(r1.getRequestId(), MaintenanceStatus.InProgress, null);
        maintenanceService.updateStatus(r2.getRequestId(), MaintenanceStatus.InProgress, null);
        assertEquals(EquipmentCondition.UnderRepair, condition(working));
        assertEquals(EquipmentCondition.Retired, condition(retired));

        maintenanceService.updateStatus(r1.getRequestId(), MaintenanceStatus.Completed, "Fixed");
        maintenanceService.updateStatus(r2.getRequestId(), MaintenanceStatus.Completed, "Inspected");
        assertEquals(EquipmentCondition.Good, condition(working));
        assertEquals(EquipmentCondition.Retired, condition(retired));
    }

    @Test
    @DisplayName("Staff see requests they raised; request history is limited to the people involved")
    public void testStaffSeesOwnRaisedRequestsAndHistoryScope() throws Exception {
        loginAs(techBUser);
        MaintenanceRequest mine = maintenanceService.createRequest(newRequest(facility, null, Priority.Low));
        assertTrue(maintenanceService.getVisibleRequests().stream().anyMatch(x -> x.getRequestId() == mine.getRequestId()));
        assertEquals(1, maintenanceService.getRequestHistory(mine.getRequestId()).size());

        loginAs(otherUser);
        assertThrows(AccessDeniedException.class, () -> maintenanceService.getRequestHistory(mine.getRequestId()));
        loginAs(techAUser);
        assertThrows(AccessDeniedException.class, () -> maintenanceService.getRequestHistory(mine.getRequestId()));
        loginAs(admin);
        assertEquals(1, maintenanceService.getRequestHistory(mine.getRequestId()).size());
    }
}