package com.fms.dao;

import com.fms.model.*;
import com.fms.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for staff management.
 * Handles database operations on the 'staff' table and associated maintenance workloads.
 *
 * Covers:
 * - FR-STF-01: CRUD and deactivation of staff records
 * - FR-STF-02: Trade specialization persistence
 * - FR-STF-03: Workload calculations and open tasks queries
 */
public class StaffDAO implements CrudDAO<Staff> {

    private static final Logger logger = LoggerFactory.getLogger(StaffDAO.class);

    private Staff mapRowToStaff(ResultSet rs) throws SQLException {
        Staff staff = new Staff();
        staff.setStaffId(rs.getInt("staff_id"));
        staff.setUserId(rs.getInt("user_id"));
        staff.setSpecialization(StaffSpecialization.fromString(rs.getString("specialization")));
        staff.setPhone(rs.getString("phone"));
        staff.setActive(rs.getBoolean("is_active"));

        // Joined user details
        try {
            staff.setFullName(rs.getString("full_name"));
            staff.setUsername(rs.getString("username"));
            staff.setEmail(rs.getString("email"));
        } catch (SQLException ignored) {
            // columns not in select projection
        }

        return staff;
    }

    @Override
    public Optional<Staff> findById(int id) {
        String sql = "SELECT s.*, u.full_name, u.username, u.email " +
                     "FROM staff s " +
                     "JOIN users u ON s.user_id = u.user_id " +
                     "WHERE s.staff_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Staff staff = mapRowToStaff(rs);
                    populateWorkload(conn, staff);
                    return Optional.of(staff);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding staff by ID {}: {}", id, e.getMessage(), e);
        }
        return Optional.empty();
    }

    public Optional<Staff> findByUserId(int userId) {
        String sql = "SELECT s.*, u.full_name, u.username, u.email " +
                     "FROM staff s " +
                     "JOIN users u ON s.user_id = u.user_id " +
                     "WHERE s.user_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Staff staff = mapRowToStaff(rs);
                    populateWorkload(conn, staff);
                    return Optional.of(staff);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding staff by user ID {}: {}", userId, e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Staff> findAll() {
        List<Staff> list = new ArrayList<>();
        String sql = "SELECT s.*, u.full_name, u.username, u.email " +
                     "FROM staff s " +
                     "JOIN users u ON s.user_id = u.user_id " +
                     "ORDER BY u.full_name ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Staff staff = mapRowToStaff(rs);
                populateWorkload(conn, staff);
                list.add(staff);
            }
        } catch (SQLException e) {
            logger.error("Error retrieving all staff members: {}", e.getMessage(), e);
        }
        return list;
    }

    @Override
    public Staff save(Staff staff) {
        String sql = "INSERT INTO staff (user_id, specialization, phone, is_active) VALUES (?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, staff.getUserId());
            pstmt.setString(2, staff.getSpecialization().getDisplayName());
            pstmt.setString(3, staff.getPhone().trim());
            pstmt.setBoolean(4, staff.isActive());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        staff.setStaffId(keys.getInt(1));
                    }
                }
            }
            logger.info("Saved new staff record for user ID {} (Staff ID: {})",
                    staff.getUserId(), staff.getStaffId());
            return staff;

        } catch (SQLException e) {
            logger.error("Error saving staff record for user ID {}: {}", staff.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Database error saving staff: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(Staff staff) {
        String sql = "UPDATE staff SET specialization = ?, phone = ?, is_active = ? WHERE staff_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, staff.getSpecialization().getDisplayName());
            pstmt.setString(2, staff.getPhone().trim());
            pstmt.setBoolean(3, staff.isActive());
            pstmt.setInt(4, staff.getStaffId());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.error("Error updating staff ID {}: {}", staff.getStaffId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        // Soft delete per FR-STF-01: set is_active = false
        String sql = "UPDATE staff SET is_active = FALSE WHERE staff_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.error("Error deactivating staff ID {}: {}", id, e.getMessage(), e);
            return false;
        }
    }

    public boolean reactivate(int id) {
        String sql = "UPDATE staff SET is_active = TRUE WHERE staff_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.error("Error reactivating staff ID {}: {}", id, e.getMessage(), e);
            return false;
        }
    }

    public boolean isUserAlreadyLinkedToStaff(int userId, int excludeStaffId) {
        String sql = "SELECT COUNT(*) FROM staff WHERE user_id = ? AND staff_id != ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            pstmt.setInt(2, excludeStaffId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking user link uniqueness for user ID {}: {}", userId, e.getMessage(), e);
        }
        return false;
    }

    /**
     * Finds users with role 'Staff' who do not yet have a profile in the 'staff' table.
     *
     * @return List of eligible User records
     */
    public List<User> findUsersEligibleForStaff() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT u.* FROM users u " +
                     "LEFT JOIN staff s ON u.user_id = s.user_id " +
                     "WHERE u.role = 'Staff' AND u.is_active = TRUE AND s.staff_id IS NULL " +
                     "ORDER BY u.full_name ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                User user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setUsername(rs.getString("username"));
                user.setFullName(rs.getString("full_name"));
                user.setEmail(rs.getString("email"));
                user.setRole(Role.Staff);
                list.add(user);
            }
        } catch (SQLException e) {
            logger.error("Error finding eligible staff users: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * Queries the live workload metrics for a staff member (FR-STF-03).
     */
    private void populateWorkload(Connection conn, Staff staff) {
        String sql = "SELECT " +
                     "  SUM(CASE WHEN status IN ('Reported', 'Assigned', 'In Progress') THEN 1 ELSE 0 END) AS open_tasks, " +
                     "  SUM(CASE WHEN status = 'In Progress' THEN 1 ELSE 0 END) AS in_progress_tasks, " +
                     "  SUM(CASE WHEN status = 'Completed' THEN 1 ELSE 0 END) AS completed_tasks " +
                     "FROM maintenance_requests " +
                     "WHERE assigned_staff = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, staff.getStaffId());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    staff.setOpenTaskCount(rs.getInt("open_tasks"));
                    staff.setInProgressTaskCount(rs.getInt("in_progress_tasks"));
                    staff.setCompletedTaskCount(rs.getInt("completed_tasks"));
                }
            }
        } catch (SQLException e) {
            logger.error("Error calculating workload for staff ID {}: {}", staff.getStaffId(), e.getMessage(), e);
        }
    }

    /**
     * Retrieves the list of open maintenance tasks assigned to a staff member (FR-STF-03).
     *
     * @param staffId The staff ID
     * @return List of open task summaries
     */
    public List<StaffTaskSummary> getStaffOpenTasks(int staffId) {
        List<StaffTaskSummary> tasks = new ArrayList<>();
        String sql = "SELECT mr.request_id, mr.facility_id, f.name AS facility_name, " +
                     "mr.equipment_id, e.name AS equipment_name, mr.description, " +
                     "mr.priority, mr.status, mr.scheduled_date, mr.created_at " +
                     "FROM maintenance_requests mr " +
                     "LEFT JOIN facilities f ON mr.facility_id = f.facility_id " +
                     "LEFT JOIN equipment e ON mr.equipment_id = e.equipment_id " +
                     "WHERE mr.assigned_staff = ? AND mr.status != 'Completed' " +
                     "ORDER BY mr.created_at DESC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, staffId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    StaffTaskSummary task = new StaffTaskSummary();
                    task.setRequestId(rs.getInt("request_id"));
                    task.setFacilityId(rs.getInt("facility_id"));
                    task.setFacilityName(rs.getString("facility_name"));

                    int eqId = rs.getInt("equipment_id");
                    if (!rs.wasNull()) {
                        task.setEquipmentId(eqId);
                        task.setEquipmentName(rs.getString("equipment_name"));
                    }

                    task.setDescription(rs.getString("description"));
                    task.setPriority(Priority.valueOf(rs.getString("priority")));
                    task.setStatus(MaintenanceStatus.valueOf(rs.getString("status")));
                    task.setScheduledDate(rs.getDate("scheduled_date"));
                    task.setCreatedAt(rs.getTimestamp("created_at"));

                    tasks.add(task);
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding open tasks for staff ID {}: {}", staffId, e.getMessage(), e);
        }
        return tasks;
    }
}
