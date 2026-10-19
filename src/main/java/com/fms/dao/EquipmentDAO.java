package com.fms.dao;

import com.fms.model.Equipment;
import com.fms.model.EquipmentCondition;
import com.fms.model.MaintenanceHistoryItem;
import com.fms.model.MaintenanceStatus;
import com.fms.model.Priority;
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
 * Data Access Object for equipment management.
 * Handles database operations on the 'equipment' table and related maintenance records.
 *
 * Covers:
 * - FR-EQP-01: save equipment linked to facility
 * - FR-EQP-02: condition tracking (Good, Needs Repair, Under Repair, Retired)
 * - FR-EQP-03: findByFacilityId and moveEquipment
 * - FR-EQP-04: getMaintenanceHistory
 */
public class EquipmentDAO implements CrudDAO<Equipment> {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentDAO.class);

    private Equipment mapRowToEquipment(ResultSet rs) throws SQLException {
        Equipment eq = new Equipment();
        eq.setEquipmentId(rs.getInt("equipment_id"));
        eq.setFacilityId(rs.getInt("facility_id"));
        eq.setName(rs.getString("name"));
        eq.setCategory(rs.getString("category"));
        eq.setSerialNo(rs.getString("serial_no"));
        eq.setPurchaseDate(rs.getDate("purchase_date"));
        eq.setConditionStatus(EquipmentCondition.fromDbValue(rs.getString("condition_status")));
        eq.setCreatedAt(rs.getTimestamp("created_at"));

        // Facility name from JOIN if present
        try {
            eq.setFacilityName(rs.getString("facility_name"));
        } catch (SQLException ignored) {
            // column not in projection
        }

        return eq;
    }

    @Override
    public Optional<Equipment> findById(int id) {
        String sql = "SELECT e.*, f.name AS facility_name " +
                     "FROM equipment e " +
                     "LEFT JOIN facilities f ON e.facility_id = f.facility_id " +
                     "WHERE e.equipment_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToEquipment(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding equipment by ID {}: {}", id, e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Equipment> findAll() {
        List<Equipment> list = new ArrayList<>();
        String sql = "SELECT e.*, f.name AS facility_name " +
                     "FROM equipment e " +
                     "LEFT JOIN facilities f ON e.facility_id = f.facility_id " +
                     "ORDER BY e.name ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapRowToEquipment(rs));
            }
        } catch (SQLException e) {
            logger.error("Error finding all equipment: {}", e.getMessage(), e);
        }
        return list;
    }

    @Override
    public Equipment save(Equipment equipment) {
        String sql = "INSERT INTO equipment (facility_id, name, category, serial_no, purchase_date, condition_status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setInt(1, equipment.getFacilityId());
            pstmt.setString(2, equipment.getName().trim());
            pstmt.setString(3, equipment.getCategory().trim());
            pstmt.setString(4, equipment.getSerialNo().trim());
            pstmt.setDate(5, equipment.getPurchaseDate());
            pstmt.setString(6, equipment.getConditionStatus().getDbValue());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        equipment.setEquipmentId(generatedKeys.getInt(1));
                    }
                }
            }
            logger.info("Saved equipment: {} (ID: {}, Serial: {})",
                    equipment.getName(), equipment.getEquipmentId(), equipment.getSerialNo());
            return equipment;

        } catch (SQLException e) {
            logger.error("Error saving equipment '{}': {}", equipment.getName(), e.getMessage(), e);
            throw new RuntimeException("Database error saving equipment: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(Equipment equipment) {
        String sql = "UPDATE equipment SET facility_id = ?, name = ?, category = ?, " +
                     "serial_no = ?, purchase_date = ?, condition_status = ? " +
                     "WHERE equipment_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, equipment.getFacilityId());
            pstmt.setString(2, equipment.getName().trim());
            pstmt.setString(3, equipment.getCategory().trim());
            pstmt.setString(4, equipment.getSerialNo().trim());
            pstmt.setDate(5, equipment.getPurchaseDate());
            pstmt.setString(6, equipment.getConditionStatus().getDbValue());
            pstmt.setInt(7, equipment.getEquipmentId());

            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.error("Error updating equipment ID {}: {}", equipment.getEquipmentId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM equipment WHERE equipment_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;

        } catch (SQLException e) {
            logger.error("Error deleting equipment ID {}: {}", id, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Lists all equipment belonging to a specific facility (FR-EQP-03).
     *
     * @param facilityId The facility ID
     * @return List of equipment items in that facility
     */
    public List<Equipment> findByFacilityId(int facilityId) {
        List<Equipment> list = new ArrayList<>();
        String sql = "SELECT e.*, f.name AS facility_name " +
                     "FROM equipment e " +
                     "LEFT JOIN facilities f ON e.facility_id = f.facility_id " +
                     "WHERE e.facility_id = ? " +
                     "ORDER BY e.name ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, facilityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToEquipment(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding equipment by facility ID {}: {}", facilityId, e.getMessage(), e);
        }
        return list;
    }

    /**
     * Finds equipment by its unique serial number.
     *
     * @param serialNo The serial number
     * @return Optional containing the equipment if found
     */
    public Optional<Equipment> findBySerialNo(String serialNo) {
        String sql = "SELECT e.*, f.name AS facility_name " +
                     "FROM equipment e " +
                     "LEFT JOIN facilities f ON e.facility_id = f.facility_id " +
                     "WHERE LOWER(e.serial_no) = LOWER(?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, serialNo.trim());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToEquipment(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding equipment by serial no '{}': {}", serialNo, e.getMessage(), e);
        }
        return Optional.empty();
    }

    /**
     * Checks if a serial number is already registered (excluding the current equipment ID).
     *
     * @param serialNo           Serial number to test
     * @param excludeEquipmentId ID to exclude (0 for new records)
     * @return true if taken
     */
    public boolean isSerialNumberTaken(String serialNo, int excludeEquipmentId) {
        String sql = "SELECT COUNT(*) FROM equipment WHERE LOWER(serial_no) = LOWER(?) AND equipment_id != ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, serialNo.trim());
            pstmt.setInt(2, excludeEquipmentId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking serial number uniqueness: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Moves an equipment item to a different facility (FR-EQP-03).
     *
     * @param equipmentId   The equipment ID
     * @param newFacilityId The destination facility ID
     * @return true if updated
     */
    public boolean moveEquipment(int equipmentId, int newFacilityId) {
        String sql = "UPDATE equipment SET facility_id = ? WHERE equipment_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, newFacilityId);
            pstmt.setInt(2, equipmentId);

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("Moved equipment ID {} to facility ID {}", equipmentId, newFacilityId);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error moving equipment ID {} to facility ID {}: {}",
                    equipmentId, newFacilityId, e.getMessage(), e);
        }
        return false;
    }

    /**
     * Updates the condition status of an equipment item (FR-EQP-02).
     *
     * @param equipmentId  The equipment ID
     * @param newCondition The new condition status
     * @return true if updated
     */
    public boolean updateCondition(int equipmentId, EquipmentCondition newCondition) {
        String sql = "UPDATE equipment SET condition_status = ? WHERE equipment_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newCondition.getDbValue());
            pstmt.setInt(2, equipmentId);

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("Updated condition of equipment ID {} to {}", equipmentId, newCondition);
                return true;
            }
        } catch (SQLException e) {
            logger.error("Error updating condition of equipment ID {}: {}", equipmentId, e.getMessage(), e);
        }
        return false;
    }

    /**
     * Searches and filters equipment items by multiple parameters.
     *
     * @param keyword    Name, serial number, or category search text
     * @param facilityId Filter by specific facility (optional)
     * @param category   Filter by category (optional)
     * @param condition  Filter by condition status (optional)
     * @return Filtered list of equipment
     */
    public List<Equipment> searchAndFilter(String keyword, Integer facilityId,
                                          String category, EquipmentCondition condition) {
        List<Equipment> results = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT e.*, f.name AS facility_name " +
                                              "FROM equipment e " +
                                              "LEFT JOIN facilities f ON e.facility_id = f.facility_id " +
                                              "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND (LOWER(e.name) LIKE ? OR LOWER(e.serial_no) LIKE ? OR LOWER(e.category) LIKE ?) ");
            String wild = "%" + keyword.trim().toLowerCase() + "%";
            params.add(wild);
            params.add(wild);
            params.add(wild);
        }

        if (facilityId != null && facilityId > 0) {
            sql.append("AND e.facility_id = ? ");
            params.add(facilityId);
        }

        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("All Categories")) {
            sql.append("AND LOWER(e.category) = LOWER(?) ");
            params.add(category.trim());
        }

        if (condition != null) {
            sql.append("AND e.condition_status = ? ");
            params.add(condition.getDbValue());
        }

        sql.append("ORDER BY e.name ASC");

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String) {
                    pstmt.setString(i + 1, (String) p);
                } else if (p instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) p);
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToEquipment(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching equipment: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * Gets all distinct equipment categories for UI filter dropdowns.
     *
     * @return List of category strings
     */
    public List<String> getDistinctCategories() {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT DISTINCT category FROM equipment ORDER BY category ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                categories.add(rs.getString("category"));
            }
        } catch (SQLException e) {
            logger.error("Error fetching distinct equipment categories: {}", e.getMessage(), e);
        }
        return categories;
    }

    /**
     * Retrieves the maintenance history of a specific equipment item (FR-EQP-04).
     *
     * @param equipmentId The equipment ID
     * @return List of maintenance history records
     */
    public List<MaintenanceHistoryItem> getMaintenanceHistory(int equipmentId) {
        List<MaintenanceHistoryItem> history = new ArrayList<>();
        String sql = "SELECT mr.request_id, mr.equipment_id, e.name AS equipment_name, " +
                     "mr.description, mr.priority, mr.status, mr.scheduled_date, " +
                     "mr.created_at, mr.completed_at, u.full_name AS staff_name " +
                     "FROM maintenance_requests mr " +
                     "LEFT JOIN equipment e ON mr.equipment_id = e.equipment_id " +
                     "LEFT JOIN staff s ON mr.assigned_staff = s.staff_id " +
                     "LEFT JOIN users u ON s.user_id = u.user_id " +
                     "WHERE mr.equipment_id = ? " +
                     "ORDER BY mr.created_at DESC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, equipmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    MaintenanceHistoryItem item = new MaintenanceHistoryItem();
                    item.setRequestId(rs.getInt("request_id"));
                    item.setEquipmentId(rs.getInt("equipment_id"));
                    item.setEquipmentName(rs.getString("equipment_name"));
                    item.setDescription(rs.getString("description"));
                    item.setPriority(Priority.valueOf(rs.getString("priority")));
                    item.setStatus(MaintenanceStatus.fromDbValue(rs.getString("status")));
                    item.setScheduledDate(rs.getDate("scheduled_date"));
                    item.setCreatedAt(rs.getTimestamp("created_at"));
                    item.setCompletedAt(rs.getTimestamp("completed_at"));
                    item.setStaffName(rs.getString("staff_name"));
                    history.add(item);
                }
            }
        } catch (SQLException e) {
            logger.error("Error retrieving maintenance history for equipment ID {}: {}",
                    equipmentId, e.getMessage(), e);
        }
        return history;
    }
}
