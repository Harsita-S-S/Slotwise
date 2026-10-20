package com.fms;

import com.fms.exception.AccessDeniedException;
import com.fms.exception.InvalidInputException;
import com.fms.model.Notification;
import com.fms.model.NotificationType;
import com.fms.model.Role;
import com.fms.model.User;
import com.fms.service.AuthService;
import com.fms.service.NotificationService;
import com.fms.service.SessionManager;
import com.fms.util.DatabaseInitializer;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SRS Module 7: FR-NOT-03 (create), FR-NOT-04 (read / mark read), FR-NOT-06 (no duplicates).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class NotificationServiceTest {

    private static NotificationService service;
    private static User userA;
    private static User userB;

    @BeforeAll
    public static void setup() throws Exception {
        DatabaseInitializer.initializeDatabase();
        service = new NotificationService();
        AuthService authService = new AuthService();

        SessionManager.getInstance().setCurrentUser(authService.login("admin", "Admin@123"));
        long ts = System.currentTimeMillis();
        userA = authService.createUser("notif_a_" + ts, "NotifPass@123", Role.User, "Notif A", "na_" + ts + "@test.com");
        userB = authService.createUser("notif_b_" + ts, "NotifPass@123", Role.User, "Notif B", "nb_" + ts + "@test.com");
    }

    @AfterAll
    public static void cleanup() {
        SessionManager.getInstance().logout();
    }

    @Test
    @Order(1)
    @DisplayName("FR-NOT-06: the same event never notifies the same user twice")
    public void testDuplicateEventIgnored() {
        assertTrue(service.notifyUser(userA.getUserId(), NotificationType.BOOKING_APPROVED, "Approved", "EVT:dup-1"));
        assertFalse(service.notifyUser(userA.getUserId(), NotificationType.BOOKING_APPROVED, "Approved again", "EVT:dup-1"));
        // same event key, different recipient is a different notification
        assertTrue(service.notifyUser(userB.getUserId(), NotificationType.BOOKING_APPROVED, "Approved", "EVT:dup-1"));
        // events without a key may repeat
        assertTrue(service.notifyUser(userA.getUserId(), NotificationType.GENERAL, "No key", null));
        assertTrue(service.notifyUser(userA.getUserId(), NotificationType.GENERAL, "No key", null));
    }

    @Test
    @Order(2)
    @DisplayName("Input validation: recipient, type, message and key length")
    public void testValidation() {
        assertThrows(InvalidInputException.class,
                () -> service.notifyUser(0, NotificationType.GENERAL, "x", null));
        assertThrows(InvalidInputException.class,
                () -> service.notifyUser(userA.getUserId(), null, "x", null));
        assertThrows(InvalidInputException.class,
                () -> service.notifyUser(userA.getUserId(), NotificationType.GENERAL, "   ", null));
        assertThrows(InvalidInputException.class,
                () -> service.notifyUser(userA.getUserId(), NotificationType.GENERAL, "x", "k".repeat(101)));
    }

    @Test
    @Order(3)
    @DisplayName("FR-NOT-04: unread count, mark read, mark all read, scoped to the logged-in user")
    public void testReadStateAndScoping() {
        service.notifyUser(userA.getUserId(), NotificationType.BOOKING_REJECTED, "Rejected", "EVT:read-1");
        service.notifyUser(userA.getUserId(), NotificationType.BOOKING_CANCELLED, "Cancelled", "EVT:read-2");

        SessionManager.getInstance().setCurrentUser(userA);
        int unreadBefore = service.getMyUnreadCount();
        assertTrue(unreadBefore >= 4);

        List<Notification> unread = service.getMyNotifications(true);
        assertEquals(unreadBefore, unread.size());
        assertTrue(unread.stream().noneMatch(Notification::isRead));
        assertTrue(unread.stream().allMatch(n -> n.getUserId() == userA.getUserId()));

        Notification first = unread.get(0);
        assertTrue(service.markRead(first.getNotifId()));
        assertFalse(service.markRead(first.getNotifId()), "already read");
        assertEquals(unreadBefore - 1, service.getMyUnreadCount());

        // another user cannot mark A's notification as read
        Notification second = service.getMyNotifications(true).get(0);
        SessionManager.getInstance().setCurrentUser(userB);
        assertFalse(service.markRead(second.getNotifId()));
        assertTrue(service.getMyNotifications(false).stream().noneMatch(n -> n.getUserId() == userA.getUserId()));

        SessionManager.getInstance().setCurrentUser(userA);
        assertEquals(unreadBefore - 1, service.markAllRead());
        assertEquals(0, service.getMyUnreadCount());
        assertTrue(service.getMyNotifications(true).isEmpty());
        assertFalse(service.getMyNotifications(false).isEmpty(), "read notifications remain in history");
    }

    @Test
    @Order(4)
    @DisplayName("Notifications require a logged-in user")
    public void testRequiresLogin() {
        SessionManager.getInstance().logout();
        assertThrows(AccessDeniedException.class, () -> service.getMyUnreadCount());
        assertThrows(AccessDeniedException.class, () -> service.getMyNotifications(false));
        assertThrows(AccessDeniedException.class, () -> service.markAllRead());
    }
}
