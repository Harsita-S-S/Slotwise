
package com.fms.service;

import com.fms.dao.MaintenanceDAO;
import com.fms.dao.StaffDAO;
import com.fms.exception.AccessDeniedException;
import com.fms.exception.DatabaseException;
import com.fms.exception.InvalidInputException;
import com.fms.model.MaintenanceLogEntry;
import com.fms.model.MaintenanceRequest;
import com.fms.model.MaintenanceStatus;
import com.fms.model.Role;
import com.fms.model.Staff;
import com.fms.model.User;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Business rules for maintenance requests.
 * Covers the core of FR-MNT-01 through FR-MNT-05.
 */
public class MaintenanceService {

    private final MaintenanceDAO maintenanceDAO;
    private final StaffDAO staffDAO;
    private final SessionManager sessionManager;

    public MaintenanceService() {
        this.maintenanceDAO = new MaintenanceDAO();
        this.staffDAO = new StaffDAO();
        this.sessionManager = SessionManager.getInstance();
    }

    /**
     * Any logged-in user may raise a maintenance request.
     */
    public MaintenanceRequest createRequest(MaintenanceRequest request)
            throws DatabaseException {

        sessionManager.requireRole(Role.User);

        if (request == null) {
            throw new InvalidInputException("Request is required.");
        }

        if (request.getFacilityId() <= 0) {
            throw new InvalidInputException(
                "facilityId", "Select a valid facility."
            );
        }

        if (request.getDescription() == null
                || request.getDescription().isBlank()) {
            throw new InvalidInputException(
                "description", "Enter a description of the problem."
            );
        }

        if (request.getPriority() == null) {
            throw new InvalidInputException(
                "priority", "Select a priority."
            );
        }

        User currentUser = sessionManager.getCurrentUser();

        // Do not trust a user ID supplied by the UI.
        request.setRaisedByUserId(currentUser.getUserId());
        request.setRequestId(0);
        request.setAssignedStaffId(null);
        request.setScheduledDate(null);
        request.setStartSlot(null);
        request.setEndSlot(null);
        request.setStatus(MaintenanceStatus.Reported);

        return maintenanceDAO.create(request);
    }

    /**
     * Admins see all requests; Staff see assigned tasks;
     * regular users see requests they raised.
     */
    public List<MaintenanceRequest> getVisibleRequests()
            throws DatabaseException {

        sessionManager.requireRole(Role.User);
        User currentUser = sessionManager.getCurrentUser();

        if (currentUser.getRole() == Role.Admin) {
            return maintenanceDAO.findAll();
        }

        if (currentUser.getRole() == Role.Staff) {
            Optional<Staff> staff =
                staffDAO.findByUserId(currentUser.getUserId());

            if (staff.isEmpty() || !staff.get().isActive()) {
                throw new AccessDeniedException(
                    "An active Staff profile is required to view assigned tasks."
                );
            }

            return maintenanceDAO.findAssignedToStaffOrRaisedBy(
                staff.get().getStaffId(),
                currentUser.getUserId()
            );
        }

        return maintenanceDAO.findByRaisedBy(currentUser.getUserId());
    }

    /**
     * View the status history of a maintenance request when the current user is allowed.
     */
    public List<MaintenanceLogEntry> getRequestHistory(int requestId)
            throws DatabaseException {

        sessionManager.requireRole(Role.User);
        User currentUser = sessionManager.getCurrentUser();

        MaintenanceRequest request = maintenanceDAO.findById(requestId)
            .orElseThrow(() -> new InvalidInputException(
                "Maintenance request not found."
            ));

        if (currentUser.getRole() == Role.Admin) {
            return maintenanceDAO.findLog(requestId);
        }

        if (currentUser.getRole() == Role.Staff) {
            Optional<Staff> staff = staffDAO.findByUserId(currentUser.getUserId());

            if (staff.isEmpty() || !staff.get().isActive()) {
                throw new AccessDeniedException(
                    "An active Staff profile is required to view request history."
                );
            }

            boolean isAssigned = request.getAssignedStaffId() != null
                && request.getAssignedStaffId() == staff.get().getStaffId();
            boolean isRaisedBy = request.getRaisedByUserId() == currentUser.getUserId();

            if (!isAssigned && !isRaisedBy) {
                throw new AccessDeniedException(
                    "You can view history only for requests assigned to you or raised by you."
                );
            }

            return maintenanceDAO.findLog(requestId);
        }

        if (request.getRaisedByUserId() != currentUser.getUserId()) {
            throw new AccessDeniedException(
                "You can view history only for requests you raised."
            );
        }

        return maintenanceDAO.findLog(requestId);
    }

    /**
     * Admin-only request assignment and scheduling.
     */
    public void assignRequest(
            int requestId,
            int staffId,
            LocalDate scheduledDate,
            int startSlot,
            int endSlot,
            String note) throws DatabaseException {

        sessionManager.requireRole(Role.Admin);

        if (requestId <= 0 || staffId <= 0) {
            throw new InvalidInputException(
                "Select a request and an active Staff member."
            );
        }

        if (scheduledDate == null) {
            throw new InvalidInputException(
                "scheduledDate", "Select a scheduled date."
            );
        }

        if (scheduledDate.isBefore(LocalDate.now())) {
            throw new InvalidInputException(
                "scheduledDate", "Maintenance cannot be scheduled in the past."
            );
        }

        if (startSlot < 1 || startSlot > 10
                || endSlot < 1 || endSlot > 10
                || startSlot > endSlot) {
            throw new InvalidInputException(
                "slotRange",
                "Select a valid slot range from 1 to 10; the end slot " +
                "must not precede the start slot."
            );
        }

        Staff staff = staffDAO.findById(staffId)
            .orElseThrow(() -> new InvalidInputException(
                "The selected Staff member does not exist."
            ));

        if (!staff.isActive()) {
            throw new InvalidInputException(
                "Only active Staff members can be assigned maintenance."
            );
        }

        maintenanceDAO.assign(
            requestId,
            staffId,
            Date.valueOf(scheduledDate),
            startSlot,
            endSlot,
            sessionManager.getCurrentUser().getUserId(),
            note
        );
    }

    /**
     * Staff can update their own assigned tasks.
     * Admins may also perform permitted status transitions.
     */
    public void updateStatus(
            int requestId,
            MaintenanceStatus newStatus,
            String completionNote) throws DatabaseException {

        sessionManager.requireRole(Role.Staff);

        if (newStatus != MaintenanceStatus.InProgress
                && newStatus != MaintenanceStatus.Completed) {
            throw new InvalidInputException(
                "Use the assignment workflow to set a request to Assigned."
            );
        }

        if (newStatus == MaintenanceStatus.Completed
                && (completionNote == null || completionNote.isBlank())) {
            throw new InvalidInputException(
                "completionNote", "Enter completion notes before completing the task."
            );
        }

        User currentUser = sessionManager.getCurrentUser();

        MaintenanceRequest request = maintenanceDAO.findById(requestId)
            .orElseThrow(() -> new InvalidInputException(
                "Maintenance request not found."
            ));

        if (currentUser.getRole() == Role.Staff) {
            Staff staff = staffDAO.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new AccessDeniedException(
                    "Your account is not linked to a Staff profile."
                ));

            if (!staff.isActive()) {
                throw new AccessDeniedException(
                    "Your Staff account is inactive."
                );
            }

            if (request.getAssignedStaffId() == null
                    || request.getAssignedStaffId() != staff.getStaffId()) {
                throw new AccessDeniedException(
                    "You can update only maintenance tasks assigned to you."
                );
            }
        }

        maintenanceDAO.transitionStatus(
            requestId,
            newStatus,
            currentUser.getUserId(),
            completionNote
        );
    }
}
