package com.fms.service;

import com.fms.dao.FacilityDAO;
import com.fms.exception.InvalidInputException;
import com.fms.model.Facility;
import com.fms.model.FacilityStatus;
import com.fms.model.FacilityType;
import com.fms.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Facility Management (Module 2).
 * Handles all business logic, validation, and role checks.
 *
 * Covers:
 * - FR-FAC-01: Admin adds facility with validation.
 * - FR-FAC-02: Admin edits facility and deactivates/reactivates.
 * - FR-FAC-03: Maintains facility status (Available, Under Maintenance, Inactive).
 * - FR-FAC-04: Search and filter facilities by type, capacity, building, and amenity.
 * - FR-FAC-05: Prevents duplicate facility names within the same building.
 * - FR-FAC-06: Refuses deactivation if future approved bookings exist unless confirmed.
 */
public class FacilityService {

    private static final Logger logger = LoggerFactory.getLogger(FacilityService.class);

    private final FacilityDAO facilityDAO;

    public FacilityService() {
        this.facilityDAO = new FacilityDAO();
    }

    // ---------------------------------------------------------------
    // 1. Add Facility (FR-FAC-01, FR-FAC-05)
    // ---------------------------------------------------------------

    /**
     * Adds a new facility to the system (Admin only).
     *
     * @param name             Facility name (e.g., "Lecture Hall 101")
     * @param type             Classroom, Laboratory, or Hall
     * @param building         Building name
     * @param floor            Floor number
     * @param capacity         Seating capacity (must be > 0)
     * @param amenities        Comma-separated amenities
     * @param requiresApproval Flag whether bookings require admin approval
     * @return The created Facility with generated ID
     */
    public Facility addFacility(String name, FacilityType type, String building,
                                int floor, int capacity, String amenities,
                                boolean requiresApproval) {
        // Enforce admin permission
        SessionManager.getInstance().requireRole(Role.Admin);

        // Step 1: Input validation
        validateFacilityInput(name, type, building, floor, capacity);

        // Step 2: Prevent duplicate name within the same building (FR-FAC-05)
        if (facilityDAO.isDuplicateNameInBuilding(name, building, 0)) {
            throw new InvalidInputException("name",
                    "A facility named '" + name.trim() + "' already exists in '" + building.trim() + "'.");
        }

        // Step 3: Create and save
        Facility facility = new Facility(
                name.trim(),
                type,
                building.trim(),
                floor,
                capacity,
                amenities != null ? amenities.trim() : "",
                requiresApproval
        );

        Facility saved = facilityDAO.save(facility);
        logger.info("Admin created facility '{}' in building '{}' (ID: {})",
                saved.getName(), saved.getBuilding(), saved.getFacilityId());
        return saved;
    }

    // ---------------------------------------------------------------
    // 2. Edit Facility (FR-FAC-02, FR-FAC-05)
    // ---------------------------------------------------------------

    /**
     * Edits existing facility details (Admin only).
     *
     * @param facility The facility with updated values
     * @return true if update succeeded
     */
    public boolean updateFacility(Facility facility) {
        // Enforce admin permission
        SessionManager.getInstance().requireRole(Role.Admin);

        if (facility == null || facility.getFacilityId() <= 0) {
            throw new InvalidInputException("facilityId", "Invalid facility ID for update.");
        }

        // Validate input
        validateFacilityInput(facility.getName(), facility.getType(),
                facility.getBuilding(), facility.getFloor(), facility.getCapacity());

        // Check duplicate name within building excluding this facility ID (FR-FAC-05)
        if (facilityDAO.isDuplicateNameInBuilding(facility.getName(), facility.getBuilding(), facility.getFacilityId())) {
            throw new InvalidInputException("name",
                    "Another facility named '" + facility.getName().trim() + "' already exists in '" + facility.getBuilding().trim() + "'.");
        }

        boolean updated = facilityDAO.update(facility);
        if (updated) {
            logger.info("Admin updated facility ID {}", facility.getFacilityId());
        }
        return updated;
    }

    // ---------------------------------------------------------------
    // 3. Status Management (FR-FAC-03)
    // ---------------------------------------------------------------

    /**
     * Updates the status of a facility (Available, Under Maintenance, Inactive).
     *
     * @param facilityId The facility ID
     * @param newStatus  The new status
     * @return true if status was updated
     */
    public boolean changeFacilityStatus(int facilityId, FacilityStatus newStatus) {
        SessionManager.getInstance().requireRole(Role.Admin);

        if (newStatus == null) {
            throw new InvalidInputException("status", "Status cannot be null.");
        }

        boolean updated = facilityDAO.updateStatus(facilityId, newStatus);
        if (updated) {
            logger.info("Admin changed facility ID {} status to {}", facilityId, newStatus);
        }
        return updated;
    }

    /**
     * Reactivates an Inactive facility, setting its status back to Available (FR-FAC-02).
     *
     * @param facilityId The facility ID
     * @return true if reactivated
     */
    public boolean reactivateFacility(int facilityId) {
        return changeFacilityStatus(facilityId, FacilityStatus.Available);
    }

    // ---------------------------------------------------------------
    // 4. Deactivation with Booking Protection (FR-FAC-06)
    // ---------------------------------------------------------------

    /**
     * Result holder for facility deactivation.
     */
    public static class DeactivationResult {
        private final boolean success;
        private final boolean confirmationRequired;
        private final int futureBookingsCount;
        private final String message;

        public DeactivationResult(boolean success, boolean confirmationRequired,
                                  int futureBookingsCount, String message) {
            this.success = success;
            this.confirmationRequired = confirmationRequired;
            this.futureBookingsCount = futureBookingsCount;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isConfirmationRequired() {
            return confirmationRequired;
        }

        public int getFutureBookingsCount() {
            return futureBookingsCount;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * Deactivates a facility with safety checks for upcoming bookings (FR-FAC-06).
     * If future approved bookings exist and confirmed is false, deactivation is refused.
     * If confirmed is true, future bookings are cancelled and users notified.
     *
     * @param facilityId The facility ID to deactivate
     * @param confirmed  Whether the Admin confirmed deactivation despite existing bookings
     * @return DeactivationResult indicating status or warning
     */
    public DeactivationResult deactivateFacility(int facilityId, boolean confirmed) {
        SessionManager.getInstance().requireRole(Role.Admin);

        Optional<Facility> opt = facilityDAO.findById(facilityId);
        if (opt.isEmpty()) {
            return new DeactivationResult(false, false, 0, "Facility not found.");
        }

        Facility facility = opt.get();

        // Check if there are future approved bookings (FR-FAC-06)
        int futureBookings = facilityDAO.countFutureApprovedBookings(facilityId);

        if (futureBookings > 0 && !confirmed) {
            // Refuse deactivation without confirmation per FR-FAC-06
            String warningMsg = "Facility '" + facility.getName() + "' has " + futureBookings +
                                " upcoming approved booking(s). Deactivation requires confirmation.";
            logger.warn("Refused deactivation for facility ID {}: {}", facilityId, warningMsg);
            return new DeactivationResult(false, true, futureBookings, warningMsg);
        }

        // If bookings exist and Admin confirmed: cancel them and notify users
        if (futureBookings > 0) {
            String cancelReason = "Facility '" + facility.getName() + "' has been deactivated for maintenance/closure.";
            facilityDAO.cancelFutureBookingsAndNotify(facilityId, cancelReason);
            logger.info("Admin confirmed deactivation: cancelled {} upcoming bookings for facility ID {}",
                    futureBookings, facilityId);
        }

        // Set facility status to Inactive (FR-FAC-02, FR-FAC-03)
        facilityDAO.updateStatus(facilityId, FacilityStatus.Inactive);

        String successMsg = "Facility '" + facility.getName() + "' has been successfully deactivated." +
                (futureBookings > 0 ? " (" + futureBookings + " upcoming bookings were cancelled and users notified)." : "");

        return new DeactivationResult(true, false, futureBookings, successMsg);
    }

    /**
     * Checks how many future approved bookings exist for a facility.
     *
     * @param facilityId The facility ID
     * @return Number of upcoming approved bookings
     */
    public int getFutureApprovedBookingsCount(int facilityId) {
        return facilityDAO.countFutureApprovedBookings(facilityId);
    }

    // ---------------------------------------------------------------
    // 5. Search & Filter (FR-FAC-04 - Accessible to all users)
    // ---------------------------------------------------------------

    /**
     * Searches and filters facilities by multiple criteria.
     * Accessible by all user roles (Admin, Staff, User).
     *
     * @param keyword     Search text (facility name or building)
     * @param type        Facility type filter
     * @param building    Building filter
     * @param minCapacity Minimum capacity filter
     * @param amenity     Amenity keyword
     * @param status      Status filter
     * @return Filtered list of facilities
     */
    public List<Facility> searchFacilities(String keyword, FacilityType type,
                                           String building, Integer minCapacity,
                                           String amenity, FacilityStatus status) {
        return facilityDAO.searchAndFilter(keyword, type, building, minCapacity, amenity, status);
    }

    /**
     * Gets all facilities in the system.
     *
     * @return List of all facilities
     */
    public List<Facility> getAllFacilities() {
        return facilityDAO.findAll();
    }

    /**
     * Gets all active facilities (Available or Under Maintenance).
     *
     * @return List of active facilities
     */
    public List<Facility> getActiveFacilities() {
        return facilityDAO.findAllActive();
    }

    /**
     * Finds a single facility by its ID.
     *
     * @param facilityId The facility ID
     * @return Optional containing the facility if found
     */
    public Optional<Facility> getFacilityById(int facilityId) {
        return facilityDAO.findById(facilityId);
    }

    /**
     * Gets a list of distinct building names for UI dropdown filters.
     *
     * @return List of building names
     */
    public List<String> getAllBuildings() {
        return facilityDAO.getDistinctBuildings();
    }

    // ---------------------------------------------------------------
    // Helper: Input Validation
    // ---------------------------------------------------------------

    private void validateFacilityInput(String name, FacilityType type, String building,
                                       int floor, int capacity) {
        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("name", "Facility name cannot be empty.");
        }

        if (name.trim().length() > 100) {
            throw new InvalidInputException("name", "Facility name cannot exceed 100 characters.");
        }

        if (type == null) {
            throw new InvalidInputException("type", "Please select a valid facility type (Classroom, Laboratory, Hall).");
        }

        if (building == null || building.trim().isEmpty()) {
            throw new InvalidInputException("building", "Building name cannot be empty.");
        }

        if (building.trim().length() > 100) {
            throw new InvalidInputException("building", "Building name cannot exceed 100 characters.");
        }

        if (floor < 0) {
            throw new InvalidInputException("floor", "Floor number cannot be negative.");
        }

        if (capacity <= 0) {
            throw new InvalidInputException("capacity", "Capacity must be greater than zero.");
        }
    }
}
