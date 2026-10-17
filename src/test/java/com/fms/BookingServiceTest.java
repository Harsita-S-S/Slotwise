package com.fms;

import com.fms.dao.BookingDAO;
import com.fms.dao.FacilityDAO;
import com.fms.dao.UserDAO;
import com.fms.exception.AccessDeniedException;
import com.fms.exception.BookingConflictException;
import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import com.fms.service.AuthService;
import com.fms.service.BookingService;
import com.fms.service.FacilityService;
import com.fms.service.SessionManager;
import com.fms.util.DatabaseInitializer;
import org.junit.jupiter.api.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BookingServiceTest {

    private static BookingService bookingService;
    private static FacilityService facilityService;
    private static AuthService authService;
    private static FacilityDAO facilityDAO;
    private static BookingDAO bookingDAO;
    private static UserDAO userDAO;

    private static User testUser;
    private static User testAdmin;
    private static Facility autoFacility;
    private static Facility approvalFacility;

    @BeforeAll
    public static void setup() throws Exception {
        DatabaseInitializer.initializeDatabase();
        bookingService = new BookingService();
        facilityService = new FacilityService();
        authService = new AuthService();
        facilityDAO = new FacilityDAO();
        bookingDAO = new BookingDAO();
        userDAO = new UserDAO();

        // Login as admin to seed test facilities and users
        testAdmin = authService.login("admin", "Admin@123");
        SessionManager.getInstance().setCurrentUser(testAdmin);

        long timestamp = System.currentTimeMillis();
        testUser = authService.createUser("booker_" + timestamp, "BookerPass@123", Role.User, "Test Booker", "booking_" + timestamp + "@test.com");

        // Facility 1: Auto-approved (requiresApproval = false, capacity = 40)
        autoFacility = facilityService.addFacility(
                "Auto Hall " + timestamp,
                FacilityType.Classroom,
                "Bldg Alpha",
                1,
                40,
                "Projector, AC",
                false
        );

        // Facility 2: Requires approval (requiresApproval = true, capacity = 100)
        approvalFacility = facilityService.addFacility(
                "Approval Aud " + timestamp,
                FacilityType.Hall,
                "Bldg Beta",
                2,
                100,
                "Sound System, Stage",
                true
        );
    }

    @BeforeEach
    public void setSessionToUser() {
        SessionManager.getInstance().setCurrentUser(testUser);
    }

    @Test
    @Order(1)
    @DisplayName("FR-BKG-03, BR-04: Instant Booking Approval for requires-approval=false")
    public void testInstantApprovalBooking() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(5);

        List<Booking> bookings = bookingService.createBooking(
                autoFacility.getFacilityId(),
                targetDate,
                List.of(2, 3), // 2 slots (BR-03)
                "Computer Science Seminar",
                35 // 35 <= 40 capacity
        );

        assertNotNull(bookings);
        assertEquals(2, bookings.size());
        for (Booking b : bookings) {
            assertEquals(BookingStatus.Approved, b.getStatus());
            assertEquals(1, b.getActiveFlag());
            assertTrue(b.getBookingId() > 0);
        }
    }

    @Test
    @Order(2)
    @DisplayName("FR-BKG-03, BR-04: Pending Approval for requires-approval=true")
    public void testPendingApprovalBooking() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(6);

        List<Booking> bookings = bookingService.createBooking(
                approvalFacility.getFacilityId(),
                targetDate,
                List.of(4),
                "Annual Fest Rehearsal",
                80
        );

        assertNotNull(bookings);
        assertEquals(1, bookings.size());
        Booking b = bookings.get(0);
        assertEquals(BookingStatus.Pending, b.getStatus());
        assertEquals(1, b.getActiveFlag());
    }

    @Test
    @Order(3)
    @DisplayName("FR-BKG-04, FR-BKG-05, BR-01: Double Booking Conflict Detection")
    public void testDoubleBookingConflict() {
        LocalDate targetDate = LocalDate.now().plusDays(5);

        // Slot 2 was already booked in test 1 for autoFacility
        BookingConflictException ex = assertThrows(BookingConflictException.class, () ->
                bookingService.createBooking(
                        autoFacility.getFacilityId(),
                        targetDate,
                        List.of(2), // Clashes with existing booking
                        "Conflicting Workshop",
                        20
                )
        );

        assertTrue(ex.getMessage().contains("Slot 2") || ex.getMessage().contains("already booked"));
    }

    @Test
    @Order(4)
    @DisplayName("BR-03: Max 3 Slots Validation")
    public void testMaxSlotsValidation() {
        LocalDate targetDate = LocalDate.now().plusDays(7);

        // Attempt to book 4 slots
        InvalidInputException ex = assertThrows(InvalidInputException.class, () ->
                bookingService.createBooking(
                        autoFacility.getFacilityId(),
                        targetDate,
                        List.of(1, 2, 3, 4), // 4 slots exceeds BR-03 limit
                        "Overlong Meeting",
                        15
                )
        );

        assertTrue(ex.getMessage().contains("between 1 and 3 slots"));
    }

    @Test
    @Order(5)
    @DisplayName("BR-02: Date Range Limits (Past Date & 30-Day Window)")
    public void testDateLimits() {
        // Past date
        assertThrows(InvalidInputException.class, () ->
                bookingService.createBooking(
                        autoFacility.getFacilityId(),
                        LocalDate.now().minusDays(1),
                        List.of(1),
                        "Past Event",
                        10
                )
        );

        // Beyond 30 days
        assertThrows(InvalidInputException.class, () ->
                bookingService.createBooking(
                        autoFacility.getFacilityId(),
                        LocalDate.now().plusDays(35),
                        List.of(1),
                        "Too Far Event",
                        10
                )
        );
    }

    @Test
    @Order(6)
    @DisplayName("FR-BKG-03: Attendee Capacity Validation")
    public void testCapacityValidation() {
        LocalDate targetDate = LocalDate.now().plusDays(8);

        // AutoFacility capacity is 40; requesting for 65 attendees
        InvalidInputException ex = assertThrows(InvalidInputException.class, () ->
                bookingService.createBooking(
                        autoFacility.getFacilityId(),
                        targetDate,
                        List.of(1),
                        "Overflow Event",
                        65
                )
        );

        assertTrue(ex.getMessage().contains("exceeds facility capacity"));
    }

    @Test
    @Order(7)
    @DisplayName("BR-05, FR-BKG-08: Cancellation and Slot Freeing for Rebooking")
    public void testCancelBookingAndRebook() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(10);

        // Book slot 5
        List<Booking> bookings = bookingService.createBooking(
                autoFacility.getFacilityId(),
                targetDate,
                List.of(5),
                "Temporary Booking",
                25
        );
        int bookingId = bookings.get(0).getBookingId();

        // Cancel it
        boolean cancelled = bookingService.cancelBooking(bookingId);
        assertTrue(cancelled);

        // Now slot 5 should be free again to rebook
        List<Booking> rebooked = bookingService.createBooking(
                autoFacility.getFacilityId(),
                targetDate,
                List.of(5),
                "Rebooked Meeting",
                20
        );
        assertNotNull(rebooked);
        assertEquals(1, rebooked.size());
    }

    @Test
    @Order(8)
    @DisplayName("FR-BKG-07: Admin Approval and Rejection with Reason")
    public void testAdminApproveAndReject() throws Exception {
        LocalDate targetDate = LocalDate.now().plusDays(12);

        // Create 2 pending bookings on approvalFacility
        List<Booking> b1List = bookingService.createBooking(approvalFacility.getFacilityId(), targetDate, List.of(2), "Pending Meet 1", 30);
        List<Booking> b2List = bookingService.createBooking(approvalFacility.getFacilityId(), targetDate, List.of(3), "Pending Meet 2", 30);

        int b1Id = b1List.get(0).getBookingId();
        int b2Id = b2List.get(0).getBookingId();

        // Switch to Admin
        SessionManager.getInstance().setCurrentUser(testAdmin);

        // Approve b1
        boolean approved = bookingService.approveBooking(b1Id);
        assertTrue(approved);
        Booking afterApprove = bookingDAO.findById(b1Id).orElseThrow();
        assertEquals(BookingStatus.Approved, afterApprove.getStatus());

        // Reject b2 with reason
        boolean rejected = bookingService.rejectBooking(b2Id, "Auditorium needed for Accreditation Committee.");
        assertTrue(rejected);
        Booking afterReject = bookingDAO.findById(b2Id).orElseThrow();
        assertEquals(BookingStatus.Rejected, afterReject.getStatus());
        assertEquals("Auditorium needed for Accreditation Committee.", afterReject.getRejectionReason());
        assertNull(afterReject.getActiveFlag()); // Slot is freed!
    }

    @Test
    @Order(9)
    @DisplayName("FR-BKG-11: Reschedule Booking as Cancel and Rebook in Single Transaction")
    public void testRescheduleBooking() throws Exception {
        LocalDate origDate = LocalDate.now().plusDays(14);
        LocalDate newDate = LocalDate.now().plusDays(15);

        List<Booking> original = bookingService.createBooking(
                autoFacility.getFacilityId(),
                origDate,
                List.of(1),
                "Original Session",
                25
        );
        int origId = original.get(0).getBookingId();

        // Reschedule to new date and slot 2
        List<Booking> rescheduled = bookingService.rescheduleBooking(
                origId,
                autoFacility.getFacilityId(),
                newDate,
                List.of(2),
                "Rescheduled Session",
                30
        );

        assertNotNull(rescheduled);
        assertEquals(1, rescheduled.size());

        // Verify old booking is Cancelled
        Booking oldAfter = bookingDAO.findById(origId).orElseThrow();
        assertEquals(BookingStatus.Cancelled, oldAfter.getStatus());
    }
}
