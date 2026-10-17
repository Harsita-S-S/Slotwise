package com.fms.service;

import com.fms.dao.StaffDAO;
import com.fms.dao.UserDAO;
import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Staff Management (Module 4).
 * Enforces Admin access controls and coordinates staff profiles with user authentication accounts.
 *
 * Covers:
 * - FR-STF-01: Admin adds, edits, and deactivates staff records with linked login account.
 * - FR-STF-02: Enforces trade specializations (Electrician, Lab Technician, Plumber, IT Support, General).
 * - FR-STF-03: Displays live workload metrics and open tasks for each staff member.
 */
public class StaffService {

    private static final Logger logger = LoggerFactory.getLogger(StaffService.class);

    private final StaffDAO staffDAO;
    private final UserDAO userDAO;

    public StaffService() {
        this.staffDAO = new StaffDAO();
        this.userDAO = new UserDAO();
    }

    // ---------------------------------------------------------------
    // 1. Add Staff Record (FR-STF-01, FR-STF-02)
    // ---------------------------------------------------------------

    /**
     * Adds a new staff record linking to an existing User login account (Admin only).
     *
     * @param userId         The user account ID (must have role Staff)
     * @param specialization The trade specialization
     * @param phone          Contact phone number
     * @return The saved Staff entity
     */
    public Staff addStaff(int userId, StaffSpecialization specialization, String phone) {
        SessionManager.getInstance().requireRole(Role.Admin);

        // Validate user existence and role
        Optional<User> userOpt = userDAO.findById(userId);
        if (userOpt.isEmpty()) {
            throw new InvalidInputException("userId", "Selected user ID " + userId + " does not exist.");
        }

        User user = userOpt.get();
        if (user.getRole() != Role.Staff) {
            throw new InvalidInputException("userId",
                    "User '" + user.getUsername() + "' has role " + user.getRole() + ". Only Staff role accounts can be linked.");
        }

        // Prevent duplicate link
        if (staffDAO.isUserAlreadyLinkedToStaff(userId, 0)) {
            throw new InvalidInputException("userId",
                    "User '" + user.getUsername() + "' is already linked to an existing staff profile.");
        }

        // Validate phone and specialization
        validateStaffInput(specialization, phone);

        Staff staff = new Staff(userId, specialization, phone.trim());
        Staff saved = staffDAO.save(staff);
        saved.setFullName(user.getFullName());
        saved.setUsername(user.getUsername());
        saved.setEmail(user.getEmail());

        logger.info("Admin created staff record: {} [{}] (Staff ID: {})",
                saved.getFullName(), saved.getSpecialization(), saved.getStaffId());

        return saved;
    }

    // ---------------------------------------------------------------
    // 2. Edit Staff Record (FR-STF-01)
    // ---------------------------------------------------------------

    /**
     * Updates staff details such as specialization or phone number (Admin only).
     *
     * @param staffId        The staff ID
     * @param specialization Updated specialization
     * @param phone          Updated contact phone
     * @param active         Active status
     * @return true if updated
     */
    public boolean updateStaff(int staffId, StaffSpecialization specialization, String phone, boolean active) {
        SessionManager.getInstance().requireRole(Role.Admin);

        Optional<Staff> opt = staffDAO.findById(staffId);
        if (opt.isEmpty()) {
            throw new InvalidInputException("staffId", "Staff record ID " + staffId + " not found.");
        }

        validateStaffInput(specialization, phone);

        Staff staff = opt.get();
        staff.setSpecialization(specialization);
        staff.setPhone(phone.trim());
        staff.setActive(active);

        boolean updated = staffDAO.update(staff);
        if (updated) {
            logger.info("Admin updated staff ID {}: {} [{}]", staffId, staff.getFullName(), specialization);
        }
        return updated;
    }

    // ---------------------------------------------------------------
    // 3. Deactivate / Reactivate Staff (FR-STF-01)
    // ---------------------------------------------------------------

    /**
     * Deactivates a staff member (Admin only - soft delete).
     *
     * @param staffId The staff ID
     * @return true if deactivated
     */
    public boolean deactivateStaff(int staffId) {
        SessionManager.getInstance().requireRole(Role.Admin);

        boolean success = staffDAO.delete(staffId);
        if (success) {
            logger.info("Admin deactivated staff ID {}", staffId);
        }
        return success;
    }

    /**
     * Reactivates an inactive staff member (Admin only).
     *
     * @param staffId The staff ID
     * @return true if reactivated
     */
    public boolean reactivateStaff(int staffId) {
        SessionManager.getInstance().requireRole(Role.Admin);

        boolean success = staffDAO.reactivate(staffId);
        if (success) {
            logger.info("Admin reactivated staff ID {}", staffId);
        }
        return success;
    }

    // ---------------------------------------------------------------
    // 4. Workload & Task Tracking (FR-STF-03)
    // ---------------------------------------------------------------

    /**
     * Retrieves all staff members with live workload counts.
     * Accessible by Admin and Staff.
     *
     * @return List of staff members with open task counters populated
     */
    public List<Staff> getAllStaff() {
        return staffDAO.findAll();
    }

    /**
     * Gets a single staff member by ID with workload details.
     *
     * @param staffId Staff ID
     * @return Optional containing the Staff member
     */
    public Optional<Staff> getStaffById(int staffId) {
        return staffDAO.findById(staffId);
    }

    /**
     * Finds staff record by linked User ID.
     *
     * @param userId The User ID
     * @return Optional containing Staff
     */
    public Optional<Staff> getStaffByUserId(int userId) {
        return staffDAO.findByUserId(userId);
    }

    /**
     * Retrieves all open maintenance tasks assigned to a staff member (FR-STF-03).
     *
     * @param staffId Staff ID
     * @return List of open task summaries
     */
    public List<StaffTaskSummary> getStaffOpenTasks(int staffId) {
        return staffDAO.getStaffOpenTasks(staffId);
    }

    /**
     * Finds active User accounts with role 'Staff' that are not yet linked to a staff record.
     *
     * @return List of eligible Users for staff registration dropdown
     */
    public List<User> getAvailableUsersForStaff() {
        return staffDAO.findUsersEligibleForStaff();
    }

    /**
     * Searches and filters staff records by name/contact, trade specialization, and active status.
     *
     * @param keyword        Search keyword for full name, username, email, or phone
     * @param specialization Optional specialization filter
     * @param activeOnly     Optional active status filter (true = active only, false = inactive only, null = all)
     * @return Filtered list of Staff members
     */
    public List<Staff> searchStaff(String keyword, StaffSpecialization specialization, Boolean activeOnly) {
        List<Staff> all = staffDAO.findAll();
        List<Staff> filtered = new java.util.ArrayList<>();

        String kw = (keyword != null) ? keyword.trim().toLowerCase() : "";

        for (int i = 0; i < all.size(); i++) {
            Staff s = all.get(i);

            // Filter 1: Active status
            if (activeOnly != null && s.isActive() != activeOnly) {
                continue;
            }

            // Filter 2: Specialization
            if (specialization != null && s.getSpecialization() != specialization) {
                continue;
            }

            // Filter 3: Search keyword
            if (!kw.isEmpty()) {
                boolean matchName = s.getFullName() != null && s.getFullName().toLowerCase().contains(kw);
                boolean matchUsername = s.getUsername() != null && s.getUsername().toLowerCase().contains(kw);
                boolean matchEmail = s.getEmail() != null && s.getEmail().toLowerCase().contains(kw);
                boolean matchPhone = s.getPhone() != null && s.getPhone().toLowerCase().contains(kw);

                if (!matchName && !matchUsername && !matchEmail && !matchPhone) {
                    continue;
                }
            }

            filtered.add(s);
        }

        return filtered;
    }

    // ---------------------------------------------------------------
    // Validation Helper
    // ---------------------------------------------------------------

    private void validateStaffInput(StaffSpecialization specialization, String phone) {
        if (specialization == null) {
            throw new InvalidInputException("specialization", "Please select a valid staff trade specialization.");
        }

        if (phone == null || phone.trim().isEmpty()) {
            throw new InvalidInputException("phone", "Contact phone number cannot be empty.");
        }

        if (phone.trim().length() > 20) {
            throw new InvalidInputException("phone", "Phone number cannot exceed 20 characters.");
        }
    }
}
