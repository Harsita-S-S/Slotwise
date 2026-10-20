package com.fms;

import com.fms.dao.NotificationDAO;
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

/**
 * FR-NOT-03: booking approved / rejected / cancelled create a notification for the booking owner,
 * once, in the same transaction as the status change.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BookingNotificationTest {

    private static BookingService bookingService;
    private static NotificationDAO notificationDAO;
    private static User admin;
    private static User owner;
    private static Facility approvalFacility;
    private static LocalDate date;

    @BeforeAll
    public static void setup() throws Exception {
        DatabaseInitializer.initializeDatabase();
        bookingService = new BookingService();
        notificationDAO = new NotificationDAO();
        AuthService authService = new AuthService();
        FacilityService facilityService = new FacilityService();

        admin = authService.login("admin", "Admin@123");
        SessionManager.getInstance().setCurrentUser(admin);
        long ts = System.currentTimeMillis();
        owner = authService.createUser("bn_owner_" + ts, "OwnerPass@123", Role.User, "Booking Owner", "bn_" + ts + "@test.com");
        approvalFacility = facilityService.addFacility("Notif Hall " + ts, FacilityType.Hall, "Bldg Notif", 1, 80,
                "Projector", true);
        date = LocalDate.now().plusDays(10);
    }

    @AfterAll
    public static void cleanup() {
        SessionManager.getInstance().logout();
    }

    private Booking pendingBooking(int slot) throws Exception {
        SessionManager.getInstance().setCurrentUser(owner);
        List<Booking> created = bookingService.createBooking(
                approvalFacility.getFacilityId(), date, List.of(slot), "Notification test", 10);
        assertEquals(BookingStatus.Pending, created.get(0).getStatus());
        return created.get(0);
    }

    private List<Notification> ownerNotifications() {
        return notificationDAO.findByUser(owner.getUserId(), false, 100);
    }

    private long countOfType(NotificationType type) {
        return ownerNotifications().stream().filter(n -> n.getType().equals(type.name())).count();
    }

    @Test
    @Order(1)
    @DisplayName("Approving a pending booking notifies the owner exactly once")
    public void testApprovalNotifies() throws Exception {
        Booking b = pendingBooking(1);
        SessionManager.getInstance().setCurrentUser(admin);

        assertTrue(bookingService.approveBooking(b.getBookingId()));

        Notification n = ownerNotifications().stream()
                .filter(x -> x.getType().equals("BOOKING_APPROVED")).findFirst().orElseThrow();
        assertEquals("BOOKING_APPROVED:" + b.getBookingId(), n.getRefKey());
        assertFalse(n.isRead());
        assertTrue(n.getMessage().contains(approvalFacility.getName()));

        // a second approval is refused and does not add a second notification
        assertThrows(InvalidInputException.class, () -> bookingService.approveBooking(b.getBookingId()));
        assertEquals(1, countOfType(NotificationType.BOOKING_APPROVED));
    }

    @Test
    @Order(2)
    @DisplayName("Rejecting a pending booking notifies the owner and includes the reason")
    public void testRejectionNotifiesWithReason() throws Exception {
        Booking b = pendingBooking(2);
        SessionManager.getInstance().setCurrentUser(admin);

        assertTrue(bookingService.rejectBooking(b.getBookingId(), "Clashes with an exam."));

        Notification n = ownerNotifications().stream()
                .filter(x -> x.getType().equals("BOOKING_REJECTED")).findFirst().orElseThrow();
        assertEquals("BOOKING_REJECTED:" + b.getBookingId(), n.getRefKey());
        assertTrue(n.getMessage().contains("Clashes with an exam."));
    }

    @Test
    @Order(3)
    @DisplayName("An Admin cancelling someone's booking notifies the owner; owner cancelling their own does not")
    public void testCancellationNotifications() throws Exception {
        Booking adminCancelled = pendingBooking(3);
        Booking selfCancelled = pendingBooking(4);

        SessionManager.getInstance().setCurrentUser(admin);
        assertTrue(bookingService.cancelBooking(adminCancelled.getBookingId()));
        assertEquals(1, countOfType(NotificationType.BOOKING_CANCELLED));
        Notification n = ownerNotifications().stream()
                .filter(x -> x.getType().equals("BOOKING_CANCELLED")).findFirst().orElseThrow();
        assertEquals("BOOKING_CANCELLED:" + adminCancelled.getBookingId(), n.getRefKey());

        SessionManager.getInstance().setCurrentUser(owner);
        assertTrue(bookingService.cancelBooking(selfCancelled.getBookingId()));
        assertEquals(1, countOfType(NotificationType.BOOKING_CANCELLED), "no self-notification");
    }

    @Test
    @Order(4)
    @DisplayName("Notifications belong to the owner, not to the admin who acted")
    public void testAdminNotNotified() throws Exception {
        long adminBefore = notificationDAO.findByUser(admin.getUserId(), false, 500).stream()
                .filter(x -> x.getRefKey() != null && x.getRefKey().startsWith("BOOKING_")).count();
        Booking b = pendingBooking(5);
        SessionManager.getInstance().setCurrentUser(admin);
        bookingService.approveBooking(b.getBookingId());
        long adminAfter = notificationDAO.findByUser(admin.getUserId(), false, 500).stream()
                .filter(x -> x.getRefKey() != null && x.getRefKey().startsWith("BOOKING_")).count();
        assertEquals(adminBefore, adminAfter);
    }
}
