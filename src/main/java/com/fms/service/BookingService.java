package com.fms.service;

import com.fms.dao.BookingDAO;
import com.fms.dao.FacilityDAO;
import com.fms.exception.AccessDeniedException;
import com.fms.exception.BookingConflictException;
import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import com.fms.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Business service layer for facility search, availability matrices, reservations,
 * admin approvals, cancellations, and transactional rescheduling.
 * Adheres to SRS Section 4.5 (FR-BKG-01 to FR-BKG-11), BR-01 to BR-06, and Section 7.3.
 */
public class BookingService {
    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private final BookingDAO bookingDAO;
    private final FacilityDAO facilityDAO;

    public BookingService() {
        this.bookingDAO = new BookingDAO();
        this.facilityDAO = new FacilityDAO();
    }

    public BookingService(BookingDAO bookingDAO, FacilityDAO facilityDAO) {
        this.bookingDAO = bookingDAO;
        this.facilityDAO = facilityDAO;
    }

    /**
     * Creates a new booking reservation for 1 to 3 time slots in a single ACID transaction (BR-01, BR-03, Sec 7.3).
     *
     * @param facilityId Target room
     * @param bookingDate Reservation date (BR-02: today up to +30 days)
     * @param slots List of slot numbers (1-10, up to 3 slots)
     * @param purpose Reason for booking
     * @param attendees Expected attendees count (FR-BKG-03: <= capacity)
     * @return List of created Booking objects
     * @throws BookingConflictException if any slot clashes with an existing booking or maintenance
     */
    public List<Booking> createBooking(int facilityId, LocalDate bookingDate, List<Integer> slots,
                                      String purpose, int attendees) throws BookingConflictException {
        return createBooking(facilityId, bookingDate, slots, purpose, attendees, false, false);
    }

    /**
     * Overloaded createBooking with extended request and capacity override flags.
     * - Admin can book up to 10 slots and bypass capacity limits.
     * - Non-admin can submit extended bookings (>3 slots) which are automatically routed to Pending approval.
     */
    public List<Booking> createBooking(int facilityId, LocalDate bookingDate, List<Integer> slots,
                                      String purpose, int attendees, boolean requestExtendedApproval, boolean allowCapacityOverride)
            throws BookingConflictException {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("User must be logged in to create a booking.");
        }

        boolean isAdmin = currentUser.getRole() == Role.Admin;

        // BR-02: Date validation
        validateBookingDate(bookingDate);

        // Slot threshold validation (BR-03 with Extended Request support)
        if (slots == null || slots.isEmpty() || slots.size() > 10) {
            throw new InvalidInputException("slots", "You must select between 1 and 10 slots.");
        }

        boolean isExtended = slots.size() > 3;
        if (isExtended && !isAdmin && !requestExtendedApproval) {
            throw new InvalidInputException("slots", "You must select between 1 and 3 slots per booking request (BR-03). For extended bookings (>3 slots), please submit as an Extended Booking Request for Admin approval.");
        }

        // Validate individual slot numbers
        Set<Integer> uniqueSlots = new HashSet<>(slots);
        if (uniqueSlots.size() != slots.size()) {
            throw new InvalidInputException("slots", "Duplicate slots in the same request are not allowed.");
        }
        for (int slot : slots) {
            if (slot < 1 || slot > 10) {
                throw new InvalidInputException("slots", "Slot number must be between 1 and 10.");
            }
            // Check if slot in the past today
            if (bookingDate.isEqual(LocalDate.now())) {
                int currentSlot = getCurrentSlotNumber();
                if (slot <= currentSlot) {
                    throw new InvalidInputException("slots", "Slot " + slot + " has already elapsed today.");
                }
            }
        }

        // Validate purpose
        if (purpose == null || purpose.trim().isEmpty()) {
            throw new InvalidInputException("purpose", "Booking purpose cannot be empty.");
        }

        // Validate facility
        Facility facility = facilityDAO.findById(facilityId)
                .orElseThrow(() -> new InvalidInputException("facilityId", "Facility not found."));

        if (facility.getStatus() == FacilityStatus.Inactive) {
            throw new InvalidInputException("facility", "Cannot book an inactive facility (BR-08).");
        }
        if (facility.getStatus() == FacilityStatus.Under_Maintenance) {
            throw new BookingConflictException("Facility '" + facility.getName() + "' is currently under maintenance (BR-06).");
        }

        // FR-BKG-03: Capacity check with Admin waiver support
        if (attendees <= 0) {
            throw new InvalidInputException("attendees", "Attendees count must be greater than zero.");
        }
        if (attendees > facility.getCapacity()) {
            if (isAdmin && allowCapacityOverride) {
                logger.warn("Admin {} applied capacity override: requested {} for facility {} (capacity {})",
                        currentUser.getUsername(), attendees, facility.getName(), facility.getCapacity());
            } else {
                throw new InvalidInputException("attendees", "Attendee count (" + attendees +
                        ") exceeds facility capacity (" + facility.getCapacity() + ") (FR-BKG-03).");
            }
        }

        // BR-04: Approval routing
        BookingStatus initialStatus;
        if (isAdmin) {
            initialStatus = BookingStatus.Approved;
        } else if (isExtended || facility.isRequiresApproval()) {
            initialStatus = BookingStatus.Pending;
        } else {
            initialStatus = BookingStatus.Approved;
        }

        String finalPurpose = isExtended && !isAdmin
                ? "[Extended Request - " + slots.size() + " hrs] " + purpose.trim()
                : purpose.trim();

        // Transactional insertion with concurrency locking (Section 7.3)
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                List<Booking> bookingsToCreate = new ArrayList<>();
                for (int slotNo : slots) {
                    // Check scheduled maintenance
                    if (bookingDAO.isSlotUnderMaintenance(facilityId, bookingDate, slotNo)) {
                        throw new BookingConflictException(
                                "Slot " + slotNo + " (" + Booking.formatSlotTime(slotNo) +
                                ") is under scheduled maintenance (BR-06).",
                                facilityId, bookingDate.toString(), slotNo);
                    }

                    // Check clashing active booking with SELECT ... FOR UPDATE (BR-01, Sec 7.3)
                    Optional<Booking> clashing = bookingDAO.findActiveBookingForUpdate(facilityId, bookingDate, slotNo, conn);
                    if (clashing.isPresent()) {
                        throw new BookingConflictException(
                                "Slot " + slotNo + " (" + Booking.formatSlotTime(slotNo) +
                                ") is already booked (BR-01).",
                                facilityId, bookingDate.toString(), slotNo);
                    }

                    Booking b = new Booking(facilityId, currentUser.getUserId(), bookingDate, slotNo,
                            finalPurpose, attendees, initialStatus);
                    b.setFacilityName(facility.getName());
                    b.setFacilityBuilding(facility.getBuilding());
                    b.setFacilityType(facility.getType());
                    b.setFacilityCapacity(facility.getCapacity());
                    b.setUsername(currentUser.getUsername());
                    b.setUserFullName(currentUser.getFullName());
                    bookingsToCreate.add(b);
                }

                List<Booking> created = bookingDAO.createBookings(bookingsToCreate, conn);
                conn.commit();
                logger.info("Successfully created {} booking(s) for user '{}' on facility '{}' [{}]",
                        created.size(), currentUser.getUsername(), facility.getName(), initialStatus);
                return created;

            } catch (SQLException e) {
                conn.rollback();
                // Check MySQL duplicate key error (error code 1062) on uk_facility_date_slot_active
                if (e.getErrorCode() == 1062 || e.getMessage().contains("uk_facility_date_slot_active")) {
                    logger.warn("Database unique constraint caught double booking: {}", e.getMessage());
                    throw new BookingConflictException("One of the selected slots was just booked by another user (BR-01).");
                }
                logger.error("Failed to commit booking transaction: {}", e.getMessage(), e);
                throw new RuntimeException("Database error during booking creation", e);
            } catch (Exception e) {
                conn.rollback();
                if (e instanceof BookingConflictException) {
                    throw (BookingConflictException) e;
                }
                if (e instanceof InvalidInputException) {
                    throw (InvalidInputException) e;
                }
                throw new RuntimeException("Unexpected error during booking", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database connection failure", e);
        }
    }

    /**
     * Creates bookings across a range of consecutive days (multi-day booking, e.g. 2-day conference).
     */
    public List<Booking> createMultiDayBooking(int facilityId, LocalDate startDate, LocalDate endDate,
                                              List<Integer> slots, String purpose, int attendees)
            throws BookingConflictException {
        if (startDate == null || endDate == null) {
            throw new InvalidInputException("date", "Start date and end date must be specified.");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidInputException("endDate", "End date cannot be earlier than start date.");
        }
        List<Booking> allCreated = new ArrayList<>();
        LocalDate cur = startDate;
        while (!cur.isAfter(endDate)) {
            List<Booking> dayBookings = createBooking(facilityId, cur, slots, purpose, attendees, true, false);
            allCreated.addAll(dayBookings);
            cur = cur.plusDays(1);
        }
        return allCreated;
    }

    /**
     * Retrieves facility slot summaries for the interactive visual grid (FR-BKG-01, FR-BKG-02).
     */
    public List<FacilitySlotSummary> getFacilitySlotGrid(LocalDate date, FacilityType type, Integer minCapacity, String amenity) {
        if (date == null) {
            date = LocalDate.now();
        }

        // Auto-complete passed bookings if searching for today or past
        bookingDAO.markPassedApprovedBookingsCompleted(LocalDate.now(), getCurrentSlotNumber());

        List<Facility> facilities = facilityDAO.searchAndFilter(null, type, null, minCapacity, amenity, null);
        List<FacilitySlotSummary> summaries = new ArrayList<>();

        int currentSlot = getCurrentSlotNumber();
        boolean isToday = date.isEqual(LocalDate.now());
        boolean isPast = date.isBefore(LocalDate.now());

        for (Facility f : facilities) {
            // Only active facilities in search (BR-08)
            if (f.getStatus() == FacilityStatus.Inactive) {
                continue;
            }

            Map<Integer, SlotStatus> slotStatuses = new HashMap<>();
            Map<Integer, Booking> slotBookings = bookingDAO.getActiveBookingsForFacilityAndDate(f.getFacilityId(), date);

            for (int slot = 1; slot <= 10; slot++) {
                if (isPast || (isToday && slot < currentSlot)) {
                    slotStatuses.put(slot, SlotStatus.PAST);
                } else if (f.getStatus() == FacilityStatus.Under_Maintenance || bookingDAO.isSlotUnderMaintenance(f.getFacilityId(), date, slot)) {
                    slotStatuses.put(slot, SlotStatus.MAINTENANCE);
                } else if (slotBookings.containsKey(slot)) {
                    Booking b = slotBookings.get(slot);
                    if (b.getStatus() == BookingStatus.Approved) {
                        slotStatuses.put(slot, SlotStatus.BOOKED);
                    } else if (b.getStatus() == BookingStatus.Pending) {
                        slotStatuses.put(slot, SlotStatus.PENDING);
                    } else {
                        slotStatuses.put(slot, SlotStatus.FREE);
                    }
                } else {
                    slotStatuses.put(slot, SlotStatus.FREE);
                }
            }

            summaries.add(new FacilitySlotSummary(f, slotStatuses, slotBookings));
        }

        return summaries;
    }

    /**
     * Retrieves bookings made by the currently logged-in user (FR-BKG-08).
     */
    public List<Booking> getMyBookings() {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("User must be logged in to view their bookings.");
        }
        bookingDAO.markPassedApprovedBookingsCompleted(LocalDate.now(), getCurrentSlotNumber());
        return bookingDAO.findBookingsByUser(currentUser.getUserId());
    }

    /**
     * Admin view to search and filter all bookings (FR-BKG-09).
     */
    public List<Booking> searchAllBookings(LocalDate date, Integer facilityId, Integer userId, BookingStatus status) {
        SessionManager.getInstance().requireRole(Role.Admin);
        bookingDAO.markPassedApprovedBookingsCompleted(LocalDate.now(), getCurrentSlotNumber());
        return bookingDAO.searchBookings(date, facilityId, userId, status);
    }

    /**
     * Cancels an eligible booking (BR-05, FR-BKG-08).
     * Only the owner or an Admin may cancel. Allowed until the slot starts.
     */
    public boolean cancelBooking(int bookingId) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("User must be logged in to cancel a booking.");
        }

        Booking booking = bookingDAO.findById(bookingId)
                .orElseThrow(() -> new InvalidInputException("bookingId", "Booking not found."));

        // BR-05: Check ownership or Admin role
        if (!SessionManager.getInstance().isAdmin() && booking.getUserId() != currentUser.getUserId()) {
            throw new AccessDeniedException("You are not authorized to cancel this booking (BR-05).");
        }

        if (booking.getStatus() == BookingStatus.Cancelled) {
            throw new InvalidInputException("status", "Booking is already cancelled.");
        }
        if (booking.getStatus() == BookingStatus.Completed) {
            throw new InvalidInputException("status", "Cannot cancel a completed booking.");
        }

        // BR-05: Cancellation is allowed until the slot starts
        LocalDate today = LocalDate.now();
        if (booking.getBookingDate().isBefore(today)) {
            throw new InvalidInputException("date", "Cannot cancel a past booking.");
        }
        if (booking.getBookingDate().isEqual(today)) {
            int currentSlot = getCurrentSlotNumber();
            if (booking.getSlotNo() <= currentSlot) {
                throw new InvalidInputException("slot", "Cancellation is only allowed until the slot starts (BR-05).");
            }
        }

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            boolean success = bookingDAO.cancelBooking(bookingId, conn);
            if (success) {
                logger.info("Booking ID {} cancelled by user '{}'", bookingId, currentUser.getUsername());
            }
            return success;
        } catch (SQLException e) {
            logger.error("Error cancelling booking ID {}: {}", bookingId, e.getMessage(), e);
            throw new RuntimeException("Database error during cancellation", e);
        }
    }

    /**
     * Admin approves a pending booking (FR-BKG-07).
     */
    public boolean approveBooking(int bookingId) {
        SessionManager.getInstance().requireRole(Role.Admin);

        Booking booking = bookingDAO.findById(bookingId)
                .orElseThrow(() -> new InvalidInputException("bookingId", "Booking not found."));

        if (booking.getStatus() != BookingStatus.Pending) {
            throw new InvalidInputException("status", "Only Pending bookings can be approved.");
        }

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            boolean success = bookingDAO.updateStatus(bookingId, BookingStatus.Approved, null, 1, conn);
            if (success) {
                logger.info("Admin approved booking ID {}", bookingId);
            }
            return success;
        } catch (SQLException e) {
            logger.error("Error approving booking ID {}: {}", bookingId, e.getMessage(), e);
            throw new RuntimeException("Database error during approval", e);
        }
    }

    /**
     * Admin rejects a pending booking with an optional reason shown to the user (FR-BKG-07).
     */
    public boolean rejectBooking(int bookingId, String reason) {
        SessionManager.getInstance().requireRole(Role.Admin);

        Booking booking = bookingDAO.findById(bookingId)
                .orElseThrow(() -> new InvalidInputException("bookingId", "Booking not found."));

        if (booking.getStatus() != BookingStatus.Pending) {
            throw new InvalidInputException("status", "Only Pending bookings can be rejected.");
        }

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            boolean success = bookingDAO.updateStatus(bookingId, BookingStatus.Rejected, reason, null, conn);
            if (success) {
                logger.info("Admin rejected booking ID {} with reason: '{}'", bookingId, reason);
            }
            return success;
        } catch (SQLException e) {
            logger.error("Error rejecting booking ID {}: {}", bookingId, e.getMessage(), e);
            throw new RuntimeException("Database error during rejection", e);
        }
    }

    /**
     * Reschedules an existing booking as cancel + rebook in a single transaction (FR-BKG-11).
     */
    public List<Booking> rescheduleBooking(int oldBookingId, int newFacilityId, LocalDate newDate,
                                          List<Integer> newSlots, String newPurpose, int newAttendees) throws BookingConflictException {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) {
            throw new AccessDeniedException("User must be logged in to reschedule a booking.");
        }

        Booking oldBooking = bookingDAO.findById(oldBookingId)
                .orElseThrow(() -> new InvalidInputException("bookingId", "Original booking not found."));

        if (!SessionManager.getInstance().isAdmin() && oldBooking.getUserId() != currentUser.getUserId()) {
            throw new AccessDeniedException("You are not authorized to reschedule this booking.");
        }

        validateBookingDate(newDate);

        if (newSlots == null || newSlots.isEmpty() || newSlots.size() > 3) {
            throw new InvalidInputException("slots", "Reschedule supports between 1 and 3 slots (BR-03).");
        }

        Facility facility = facilityDAO.findById(newFacilityId)
                .orElseThrow(() -> new InvalidInputException("facilityId", "Target facility not found."));

        if (facility.getStatus() != FacilityStatus.Available) {
            throw new InvalidInputException("facility", "Target facility is not currently available.");
        }

        if (newAttendees <= 0 || newAttendees > facility.getCapacity()) {
            throw new InvalidInputException("attendees", "Attendee count exceeds facility capacity (" + facility.getCapacity() + ").");
        }

        BookingStatus newStatus = facility.isRequiresApproval() ? BookingStatus.Pending : BookingStatus.Approved;

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Cancel old booking first inside transaction
                bookingDAO.cancelBooking(oldBookingId, conn);

                // 2. Validate and reserve new slots
                List<Booking> newBookings = new ArrayList<>();
                for (int slotNo : newSlots) {
                    if (bookingDAO.isSlotUnderMaintenance(newFacilityId, newDate, slotNo)) {
                        throw new BookingConflictException("Target slot " + slotNo + " is under maintenance.", newFacilityId, newDate.toString(), slotNo);
                    }

                    Optional<Booking> clashing = bookingDAO.findActiveBookingForUpdate(newFacilityId, newDate, slotNo, conn);
                    if (clashing.isPresent()) {
                        throw new BookingConflictException("Target slot " + slotNo + " is already booked.", newFacilityId, newDate.toString(), slotNo);
                    }

                    Booking b = new Booking(newFacilityId, currentUser.getUserId(), newDate, slotNo,
                            newPurpose.trim(), newAttendees, newStatus);
                    b.setFacilityName(facility.getName());
                    b.setFacilityBuilding(facility.getBuilding());
                    b.setFacilityType(facility.getType());
                    b.setFacilityCapacity(facility.getCapacity());
                    b.setUsername(currentUser.getUsername());
                    b.setUserFullName(currentUser.getFullName());
                    newBookings.add(b);
                }

                List<Booking> created = bookingDAO.createBookings(newBookings, conn);
                conn.commit();
                logger.info("Rescheduled booking ID {} into {} new booking(s) under single transaction (FR-BKG-11)",
                        oldBookingId, created.size());
                return created;

            } catch (Exception e) {
                conn.rollback();
                if (e instanceof BookingConflictException) throw (BookingConflictException) e;
                if (e instanceof InvalidInputException) throw (InvalidInputException) e;
                throw new RuntimeException("Reschedule transaction failed", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Database error during reschedule", e);
        }
    }

    /**
     * Marks passed approved bookings as Completed (FR-BKG-10).
     */
    public int autoCompletePassedBookings() {
        return bookingDAO.markPassedApprovedBookingsCompleted(LocalDate.now(), getCurrentSlotNumber());
    }

    // Helper methods

    private void validateBookingDate(LocalDate date) {
        if (date == null) {
            throw new InvalidInputException("date", "Booking date cannot be null.");
        }
        LocalDate today = LocalDate.now();
        if (date.isBefore(today)) {
            throw new InvalidInputException("date", "Bookings cannot be made in the past (BR-02).");
        }
        if (date.isAfter(today.plusDays(30))) {
            throw new InvalidInputException("date", "Bookings cannot be made more than 30 days in advance (BR-02).");
        }
    }

    public static int getCurrentSlotNumber() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();
        // Slot 1 starts at 08:00, Slot 10 ends at 18:00
        if (hour < 8) return 0; // before business hours
        if (hour >= 18) return 11; // after business hours
        return hour - 7;
    }
}
