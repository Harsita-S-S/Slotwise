
package com.fms.dao;

import com.fms.exception.DatabaseException;
import com.fms.model.MaintenanceRequest;
import com.fms.model.MaintenanceStatus;
import com.fms.model.Priority;
import com.fms.util.DBConnection;
import com.fms.exception.InvalidInputException;
import com.fms.exception.InvalidStatusTransitionException;
import com.fms.model.MaintenanceLogEntry;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC operations for maintenance requests and their status history.
 */
public class MaintenanceDAO {

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

    private List<MaintenanceRequest> queryList(String sql, int... parameter)
            throws DatabaseException {

        List<MaintenanceRequest> requests = new ArrayList<>();

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (parameter.length > 0) {
                ps.setInt(1, parameter[0]);
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
     * Assigns a Reported request to an active Staff account and schedules it.
     * Request update and status history are committed together.
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
                    "SELECT status FROM maintenance_requests " +
                    "WHERE request_id = ? FOR UPDATE";

                String oldStatus;

                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setInt(1, requestId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InvalidInputException("requestId", "Maintenance request not found.");
                        }
                        oldStatus = rs.getString("status");
                    }
                }

                if (!MaintenanceStatus.Reported.getDbValue()
                        .equalsIgnoreCase(oldStatus)) {
                    throw new InvalidStatusTransitionException("Only Reported requests can be assigned.");
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
                        ? "Request assigned and scheduled."
                        : note.trim()
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
     * Applies only the next permitted status transition.
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
                String lockSql =
                    "SELECT status FROM maintenance_requests " +
                    "WHERE request_id = ? FOR UPDATE";

                try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
                    ps.setInt(1, requestId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new InvalidInputException("requestId", "Maintenance request not found.");
                        }
                        oldStatusValue = rs.getString("status");
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
