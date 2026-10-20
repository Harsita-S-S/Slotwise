
package com.fms.dao;

import com.fms.exception.DatabaseException;
import com.fms.model.MaintenanceRequest;
import com.fms.model.MaintenanceStatus;
import com.fms.model.Priority;
import com.fms.util.DBConnection;
import com.fms.exception.InvalidInputException;
import com.fms.exception.InvalidStatusTransitionException;
import com.fms.model.MaintenanceLogEntry;
import com.fms.model.EquipmentCondition;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC operations for maintenance requests and their status history.
 */
public class MaintenanceDAO {

    private final NotificationDAO notificationDAO = new NotificationDAO();

    /** A live booking that falls inside a maintenance window (BR-07). */
    private record AffectedBooking(int bookingId, int userId, Date bookingDate, int slotNo) {
    }

    private static final String SELECT_BASE =
        "SELECT mr.*, " +
        "f.name AS facility_name, " +
        "e.name AS equipment_name, " +
        "raiser.full_name AS raised_by_name, " +
        "assigned_user.full_name AS assigned_staff_name, " +
        "(SELECT ml.note FROM maintenance_log ml " +
        " WHERE ml.request_id = mr.request_id " +
        " ORDER BY ml.changed_at DESC, ml.log_id DESC LIMIT 1) AS latest_note " +
        "FROM maintenance_requests mr " +
        "JOIN facilities f ON f.facility_id = mr.facility_id " +
        "LEFT JOIN equipment e ON e.equipment_id = mr.equipment_id " +
        "JOIN users raiser ON raiser.user_id = mr.raised_by " +
        "LEFT JOIN staff s ON s.staff_id = mr.assigned_staff " +
        "LEFT JOIN users assigned_user ON assigned_user.user_id = s.user_id ";

    private MaintenanceRequest mapRow(ResultSet rs) throws SQLException {
        MaintenanceRequest request = new MaintenanceRequest();

        request.setRequestId(rs.getInt("request_id"));
        request.setFacilityId(rs.getInt("facility_id"));

        int equipmentId = rs.getInt("equipment_id");
        request.setEquipmentId(rs.wasNull() ? null : equipmentId);

        request.setRaisedByUserId(rs.getInt("raised_by"));

        int staffId = rs.getInt("assigned_staff");
        request.setAssignedStaffId(rs.wasNull() ? null : staffId);

        request.setDescription(rs.getString("description"));
        request.setPriority(Priority.valueOf(rs.getString("priority")));
        request.setStatus(
            MaintenanceStatus.fromDbValue(rs.getString("status"))
        );

        request.setScheduledDate(rs.getDate("scheduled_date"));

        int startSlot = rs.getInt("start_slot");
        request.setStartSlot(rs.wasNull() ? null : startSlot);

        int endSlot = rs.getInt("end_slot");
        request.setEndSlot(rs.wasNull() ? null : endSlot);

        request.setCreatedAt(rs.getTimestamp("created_at"));
        request.setCompletedAt(rs.getTimestamp("completed_at"));

        request.setFacilityName(rs.getString("facility_name"));
        request.setEquipmentName(rs.getString("equipment_name"));
        request.setRaisedByName(rs.getString("raised_by_name"));
        request.setAssignedStaffName(rs.getString("assigned_staff_name"));
        request.setLatestNote(rs.getString("latest_note"));

        return request;
    }

    private void insertLog(
            Connection conn,
            int requestId,
            String oldStatus,
            String newStatus,
            int changedBy,
            String note) throws SQLException {

        String sql =
            "INSERT INTO maintenance_log " +
            "(request_id, old_status, new_status, changed_by, note) " +
            "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, requestId);
            ps.setString(2, oldStatus);
            ps.setString(3, newStatus);
            ps.setInt(4, changedBy);
            ps.setString(5, note);
            ps.executeUpdate();
        }
    }

    public MaintenanceRequest create(MaintenanceRequest request)
            throws DatabaseException {

        String sql =
            "INSERT INTO maintenance_requests " +
            "(facility_id, equipment_id, raised_by, description, priority, status) " +
            "VALUES (?, ?, ?, ?, ?, 'Reported')";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try {
                // If equipment is specified, ensure it belongs to this facility.
                if (request.getEquipmentId() != null) {
                    String check =
                        "SELECT 1 FROM equipment " +
                        "WHERE equipment_id = ? AND facility_id = ?";

                    try (PreparedStatement ps = conn.prepareStatement(check)) {
                        ps.setInt(1, request.getEquipmentId());
                        ps.setInt(2, request.getFacilityId());

                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                throw new InvalidInputException(
                                    "equipment",
                                    "The selected equipment does not belong " +
                                    "to the selected facility."
                                );
                            }
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        sql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setInt(1, request.getFacilityId());

                    if (request.getEquipmentId() == null) {
                        ps.setNull(2, Types.INTEGER);
                    } else {
                        ps.setInt(2, request.getEquipmentId());
                    }

                    ps.setInt(3, request.getRaisedByUserId());
                    ps.setString(4, request.getDescription().trim());
                    ps.setString(5, request.getPriority().name());

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException(
                                "Could not retrieve the new request ID."
                            );
                        }
                        request.setRequestId(keys.getInt(1));
                    }
                }

                insertLog(
                    conn,
                    request.getRequestId(),
                    "",
                    MaintenanceStatus.Reported.getDbValue(),
                    request.getRaisedByUserId(),
                    "Maintenance request created."
                );

                conn.commit();
                request.setStatus(MaintenanceStatus.Reported);
                return request;

            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }

                throw new DatabaseException(
                    "Unexpected error during maintenance transaction.", e
                );

            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // Connection is about to be closed.
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException(
                "Database error while creating the maintenance request.", e
            );
        }
    }

    public Optional<MaintenanceRequest> findById(int requestId)
            throws DatabaseException {

        String sql = SELECT_BASE + "WHERE mr.request_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, requestId);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next()
                    ? Optional.of(mapRow(rs))
                    : Optional.empty();
            }

        } catch (SQLException e) {
            throw new DatabaseException(
                "Could not retrieve maintenance request " + requestId, e
            );
        }
    }

    public List<MaintenanceRequest> findAll()
            throws DatabaseException {
        return queryList(SELECT_BASE + "ORDER BY mr.created_at DESC");
    }

    /**
     * Requests assigned to a Staff member plus any they raised themselves.
     */
    public List<MaintenanceRequest> findAssignedToStaffOrRaisedBy(int staffId, int userId)
            throws DatabaseException {
        return queryList(
            SELECT_BASE +
            "WHERE mr.assigned_staff = ? OR mr.raised_by = ? " +
            "ORDER BY mr.created_at DESC",
            staffId, userId
        );
    }

    public List<MaintenanceRequest> findByRaisedBy(int userId)
            throws DatabaseException {
        return queryList(
            SELECT_BASE +
            "WHERE mr.raised_by = ? ORDER BY mr.created_at DESC",
            userId
        );
    }

    public List<MaintenanceRequest> findAssignedToStaff(int staffId)
            throws DatabaseException {
        return queryList(
            SELECT_BASE +
            "WHERE mr.assigned_staff = ? ORDER BY mr.created_at DESC",
            staffId
        );
    }

    /**
     * Status history of one request, oldest first (FR-MNT-03, FR-MNT-05).
     */
    public List<MaintenanceLogEntry> findLog(int requestId)
            throws DatabaseException {

        String sql =
            "SELECT l.log_id, l.request_id, l.old_status, l.new_status, " +
            "l.changed_by, u.full_name AS changed_by_name, " +
            "l.changed_at, l.note " +
            "FROM maintenance_log l " +
            "JOIN users u ON u.user_id = l.changed_by " +
            "WHERE l.request_id = ? " +
            "ORDER BY l.changed_at, l.log_id";

        List<MaintenanceLogEntry> entries = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, requestId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MaintenanceLogEntry entry = new MaintenanceLogEntry();
                    entry.setLogId(rs.getInt("log_id"));
                    entry.setRequestId(rs.getInt("request_id"));
                    entry.setOldStatus(rs.getString("old_status"));
                    entry.setNewStatus(rs.getString("new_status"));
                    entry.setChangedBy(rs.getInt("changed_by"));
                    entry.setChangedByName(rs.getString("changed_by_name"));
                    entry.setChangedAt(rs.getTimestamp("changed_at"));
                    entry.setNote(rs.getString("note"));
                    entries.add(entry);
                }
            }

            return entries;

        } catch (SQLException e) {
            throw new DatabaseException(
                "Could not retrieve the history of request " + requestId, e
            );
        }
    }

    // ------------------------------------------------------------------
    // Side effects that must commit or roll back with the status change
    // ------------------------------------------------------------------

    private String lookupString(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private int lookupInt(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Slot n runs from 07+n:00 to 08+n:00. */
    private String slotText(int startSlot, int endSlot) {
        return String.format("%02d:00-%02d:00", 7 + startSlot, 8 + endSlot);
    }

    /**
     * BR-07 / FR-X1-02: flags live bookings that fall inside any open maintenance
     * window of the facility and notifies the owners of the newly affected ones.
     * Flags of bookings no longer inside a window are cleared (FR-X1-03).
     */
    private void flagAffectedBookings(
            Connection conn, int requestId, int facilityId, String facilityName)
            throws SQLException {

        String findSql =
            "SELECT b.booking_id, b.user_id, b.booking_date, b.slot_no " +
            "FROM bookings b " +
            "JOIN maintenance_requests mr " +
            "  ON mr.facility_id = b.facility_id " +
            " AND mr.scheduled_date = b.booking_date " +
            " AND b.slot_no BETWEEN mr.start_slot AND mr.end_slot " +
            "WHERE mr.request_id = ? " +
            "AND b.active_flag = 1 " +
            "AND b.status IN ('Pending', 'Approved') " +
            "AND b.affected_by_maintenance = FALSE";

        List<AffectedBooking> newlyAffected = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setInt(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    newlyAffected.add(new AffectedBooking(
                        rs.getInt("booking_id"),
                        rs.getInt("user_id"),
                        rs.getDate("booking_date"),
                        rs.getInt("slot_no")));
                }
            }
        }

        recomputeAffectedFlags(conn, facilityId);

        for (AffectedBooking ab : newlyAffected) {
            notificationDAO.insertIfAbsent(
                conn,
                ab.userId(),
                "Your booking of " + facilityName + " on " + ab.bookingDate() +
                " (" + slotText(ab.slotNo(), ab.slotNo()) + ") is affected by " +
                "scheduled maintenance. Please cancel it and book another slot.",
                "BOOKING_AFFECTED",
                "BOOKING-AFFECTED-" + ab.bookingId() + "-" + requestId
            );
        }
    }

    /**
     * Resets the Affected flag for the facility and re-applies it to every live
     * booking inside an Assigned or In Progress maintenance window.
     */
    private void recomputeAffectedFlags(Connection conn, int facilityId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE bookings SET affected_by_maintenance = FALSE " +
                "WHERE facility_id = ? AND affected_by_maintenance = TRUE")) {
            ps.setInt(1, facilityId);
            ps.executeUpdate();
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE bookings b " +
                "JOIN maintenance_requests mr " +
                "  ON mr.facility_id = b.facility_id " +
                " AND mr.scheduled_date = b.booking_date " +
                " AND b.slot_no BETWEEN mr.start_slot AND mr.end_slot " +
                " AND mr.status IN ('Assigned', 'In Progress') " +
                "SET b.affected_by_maintenance = TRUE " +
                "WHERE b.facility_id = ? " +
                "AND b.active_flag = 1 " +
                "AND b.status IN ('Pending', 'Approved')")) {
            ps.setInt(1, facilityId);
            ps.executeUpdate();
        }
    }

    /** Updates the equipment condition unless the item has been retired (FR-MNT-06). */
    private void setEquipmentCondition(
            Connection conn, int equipmentId, EquipmentCondition condition)
            throws SQLException {

        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE equipment SET condition_status = ? " +
                "WHERE equipment_id = ? AND condition_status <> 'Retired'")) {
            ps.setString(1, condition.getDbValue());
            ps.setInt(2, equipmentId);
            ps.executeUpdate();
        }
    }
    private List<MaintenanceRequest> queryList(String sql, int... parameter)
            throws DatabaseException {

        List<MaintenanceRequest> requests = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            for (int i = 0; i < parameter.length; i++) {
                ps.setInt(i + 1, parameter[i]);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    requests.add(mapRow(rs));
                }
            }

            return requests;

        } catch (SQLException e) {
            throw new DatabaseException(
                "Could not retrieve maintenance requests.", e
            );
        }
    }

       /**
     * Assigns a Reported request to an active Staff account and schedules it, or
     * re-assigns / reschedules a request that is still Assigned. Request update,
     * status history, affected-booking flags and notifications commit together.
     */
    public void assign(
            int requestId,
            int staffId,
            Date scheduledDate,
            int startSlot,
            int endSlot,
            int changedBy,
            String note) throws DatabaseException {

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try {
                String staffSql =
                    "SELECT s.staff_id FROM staff s " +
                    "JOIN users u ON u.user_id = s.user_id " +
                    "WHERE s.staff_id = ? AND s.is_active = TRUE " +
                    "AND u.is_active = TRUE AND u.role = 'Staff' " +
                    "FOR UPDATE";

                try (PreparedStatement ps = conn.prepareStatement(staffSql)) {
                    ps.setInt(1, staffId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InvalidInputException("staffId", "Select an active Staff member.");
                        }
                    }
                }

                String lockSql =
                    "SELECT status, facility_id FROM maintenance_requests " +
                    "WHERE request_id = ? FOR UPDATE";

                String oldStatus;
                int facilityId;

                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setInt(1, requestId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InvalidInputException("requestId", "Maintenance request not found.");
                        }
                        oldStatus = rs.getString("status");
                        facilityId = rs.getInt("facility_id");
                    }
                }

                // A request can be assigned, and re-assigned or rescheduled while still Assigned.
                boolean reassign = MaintenanceStatus.Assigned.getDbValue()
                        .equalsIgnoreCase(oldStatus);
                if (!reassign && !MaintenanceStatus.Reported.getDbValue()
                        .equalsIgnoreCase(oldStatus)) {
                    throw new InvalidStatusTransitionException(
                        "Only Reported or Assigned requests can be assigned or rescheduled."
                    );
                }

                String updateSql =
                    "UPDATE maintenance_requests SET assigned_staff = ?, " +
                    "scheduled_date = ?, start_slot = ?, end_slot = ?, " +
                    "status = 'Assigned' WHERE request_id = ?";

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, staffId);
                    ps.setDate(2, scheduledDate);
                    ps.setInt(3, startSlot);
                    ps.setInt(4, endSlot);
                    ps.setInt(5, requestId);
                    ps.executeUpdate();
                }

                insertLog(
                    conn,
                    requestId,
                    oldStatus,
                    MaintenanceStatus.Assigned.getDbValue(),
                    changedBy,
                    note == null || note.isBlank()
                        ? (reassign
                            ? "Request reassigned or rescheduled."
                            : "Request assigned and scheduled.")
                        : note.trim()
                );

                // BR-07 / FR-X1-02: flag and notify bookings inside the new window.
                String facilityName = lookupString(conn,
                    "SELECT name FROM facilities WHERE facility_id = ?", facilityId);
                flagAffectedBookings(conn, requestId, facilityId, facilityName);

                // FR-NOT-03: tell the Staff member about the task.
                int staffUserId = lookupInt(conn,
                    "SELECT user_id FROM staff WHERE staff_id = ?", staffId);
                notificationDAO.insertIfAbsent(
                    conn,
                    staffUserId,
                    "Maintenance request #" + requestId + " at " + facilityName +
                    " is assigned to you for " + scheduledDate + ", " +
                    slotText(startSlot, endSlot) + ".",
                    "MAINTENANCE_ASSIGNED",
                    "MAINT-ASSIGNED-" + requestId + "-" + staffId + "-" +
                    scheduledDate + "-" + startSlot + "-" + endSlot
                );

                conn.commit();

            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }

                throw new DatabaseException(
                    "Unexpected error during maintenance transaction.", e
                );

            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // Connection is about to be closed.
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException(
                "Database error while assigning maintenance.", e
            );
        }
    }
        /**
     * Applies only the next permitted status transition. Status, history, equipment
     * condition, affected-booking flags and notifications commit together.
     */
    public void transitionStatus(
            int requestId,
            MaintenanceStatus newStatus,
            int changedBy,
            String note) throws DatabaseException {

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            try {
                String oldStatusValue;
                int facilityId;
                Integer equipmentId;
                int raisedBy;
                String lockSql =
                    "SELECT status, facility_id, equipment_id, raised_by " +
                    "FROM maintenance_requests " +
                    "WHERE request_id = ? FOR UPDATE";

                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setInt(1, requestId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InvalidInputException("requestId", "Maintenance request not found.");
                        }
                        oldStatusValue = rs.getString("status");
                        facilityId = rs.getInt("facility_id");
                        int equipment = rs.getInt("equipment_id");
                        equipmentId = rs.wasNull() ? null : equipment;
                        raisedBy = rs.getInt("raised_by");
                    }
                }

                MaintenanceStatus oldStatus =
                    MaintenanceStatus.fromDbValue(oldStatusValue);

                boolean valid =
                    (oldStatus == MaintenanceStatus.Assigned
                        && newStatus == MaintenanceStatus.InProgress)
                    || (oldStatus == MaintenanceStatus.InProgress
                        && newStatus == MaintenanceStatus.Completed);

                if (!valid) {
                    throw new InvalidStatusTransitionException(
                        "Invalid maintenance transition: " +
                        oldStatus.getDbValue() + " to " +
                        newStatus.getDbValue() +
                        " (requests move forward one step at a time)."
                    );
                }

                String updateSql =
                    "UPDATE maintenance_requests SET status = ?, " +
                    "completed_at = CASE WHEN ? = 'Completed' " +
                    "THEN CURRENT_TIMESTAMP ELSE completed_at END " +
                    "WHERE request_id = ?";

                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setString(1, newStatus.getDbValue());
                    ps.setString(2, newStatus.getDbValue());
                    ps.setInt(3, requestId);
                    ps.executeUpdate();
                }

                insertLog(
                    conn,
                    requestId,
                    oldStatus.getDbValue(),
                    newStatus.getDbValue(),
                    changedBy,
                    note == null ? "" : note.trim()
                );

                // FR-MNT-06: equipment condition follows the work.
                if (equipmentId != null) {
                    if (newStatus == MaintenanceStatus.InProgress) {
                        setEquipmentCondition(conn, equipmentId, EquipmentCondition.UnderRepair);
                    } else if (newStatus == MaintenanceStatus.Completed) {
                        setEquipmentCondition(conn, equipmentId, EquipmentCondition.Good);
                    }
                }

                if (newStatus == MaintenanceStatus.Completed) {
                    // FR-X1-03: the window is released, so clear Affected flags.
                    recomputeAffectedFlags(conn, facilityId);

                    // FR-NOT-03: tell the person who raised the request.
                    if (raisedBy != changedBy) {
                        String facilityName = lookupString(conn,
                            "SELECT name FROM facilities WHERE facility_id = ?", facilityId);
                        notificationDAO.insertIfAbsent(
                            conn,
                            raisedBy,
                            "Your maintenance request #" + requestId + " at " +
                            facilityName + " has been completed.",
                            "MAINTENANCE_COMPLETED",
                            "MAINT-COMPLETED-" + requestId
                        );
                    }
                }

                conn.commit();

            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }

                throw new DatabaseException(
                    "Unexpected error during maintenance transaction.", e
                );

            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException ignored) {
                    // Connection is about to be closed.
                }
            }

        } catch (SQLException e) {
            throw new DatabaseException(
                "Database error while updating maintenance status.", e
            );
        }
    }
}