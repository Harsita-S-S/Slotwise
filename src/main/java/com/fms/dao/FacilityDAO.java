package com.fms.dao;

import com.fms.model.Facility;
import com.fms.model.FacilityStatus;
import com.fms.model.FacilityType;
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
 * Data Access Object for facilities.
 * Handles database operations on the 'facilities' table.
 *
 * Covers:
 * - FR-FAC-01: save new facility
 * - FR-FAC-02: update and delete (deactivate) facility
 * - FR-FAC-03: updateStatus (Available, Under Maintenance, Inactive)
 * - FR-FAC-04: searchAndFilter facilities
 * - FR-FAC-05: isDuplicateNameInBuilding
 * - FR-FAC-06: countFutureApprovedBookings and cancelFutureBookingsAndNotify
 */
public class FacilityDAO implements CrudDAO<Facility> {

    private static final Logger logger = LoggerFactory.getLogger(FacilityDAO.class);

    /**
     * Maps a ResultSet row to a Facility object.
     */
    private Facility mapRowToFacility(ResultSet rs) throws SQLException {
        Facility facility = new Facility();
        facility.setFacilityId(rs.getInt("facility_id"));
        facility.setName(rs.getString("name"));
        facility.setType(FacilityType.valueOf(rs.getString("type")));
        facility.setBuilding(rs.getString("building"));
        facility.setFloor(rs.getInt("floor"));
        facility.setCapacity(rs.getInt("capacity"));
        facility.setAmenities(rs.getString("amenities"));
        facility.setRequiresApproval(rs.getBoolean("requires_approval"));
        facility.setStatus(FacilityStatus.fromDbValue(rs.getString("status")));
        facility.setCreatedAt(rs.getTimestamp("created_at"));
        return facility;
    }

    @Override
    public Optional<Facility> findById(int id) {
        String sql = "SELECT * FROM facilities WHERE facility_id = ?";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToFacility(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding facility by ID {}: {}", id, e.getMessage(), e);
        }
        return Optional.empty();
    }

    @Override
    public List<Facility> findAll() {
        List<Facility> list = new ArrayList<>();
        String sql = "SELECT * FROM facilities ORDER BY building ASC, floor ASC, name ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapRowToFacility(rs));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving all facilities: {}", e.getMessage(), e);
        }
        return list;
    }

    /**
     * Retrieves all active facilities (excluding Inactive).
     *
     * @return List of Available or Under Maintenance facilities
     */
    public List<Facility> findAllActive() {
        List<Facility> list = new ArrayList<>();
        String sql = "SELECT * FROM facilities WHERE status != 'Inactive' ORDER BY building ASC, floor ASC, name ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                list.add(mapRowToFacility(rs));
            }
        } catch (SQLException e) {
            logger.error("Error retrieving active facilities: {}", e.getMessage(), e);
        }
        return list;
    }

    @Override
    public Facility save(Facility facility) {
        String sql = "INSERT INTO facilities (name, type, building, floor, capacity, amenities, requires_approval, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, facility.getName().trim());
            pstmt.setString(2, facility.getType().name());
            pstmt.setString(3, facility.getBuilding().trim());
            pstmt.setInt(4, facility.getFloor());
            pstmt.setInt(5, facility.getCapacity());
            pstmt.setString(6, facility.getAmenities());
            pstmt.setBoolean(7, facility.isRequiresApproval());
            pstmt.setString(8, facility.getStatus().getDbValue());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        facility.setFacilityId(generatedKeys.getInt(1));
                    }
                }
            }
            logger.info("Saved new facility: {} (ID: {})", facility.getName(), facility.getFacilityId());
            return facility;

        } catch (SQLException e) {
            logger.error("Error saving facility '{}': {}", facility.getName(), e.getMessage(), e);
            throw new RuntimeException("Database error saving facility: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean update(Facility facility) {
        String sql = "UPDATE facilities SET name = ?, type = ?, building = ?, floor = ?, " +
                     "capacity = ?, amenities = ?, requires_approval = ?, status = ? " +
                     "WHERE facility_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, facility.getName().trim());
            pstmt.setString(2, facility.getType().name());
            pstmt.setString(3, facility.getBuilding().trim());
            pstmt.setInt(4, facility.getFloor());
            pstmt.setInt(5, facility.getCapacity());
            pstmt.setString(6, facility.getAmenities());
            pstmt.setBoolean(7, facility.isRequiresApproval());
            pstmt.setString(8, facility.getStatus().getDbValue());
            pstmt.setInt(9, facility.getFacilityId());

            int rowsUpdated = pstmt.executeUpdate();
            return rowsUpdated > 0;

        } catch (SQLException e) {
            logger.error("Error updating facility ID {}: {}", facility.getFacilityId(), e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        // Soft delete per FR-FAC-02 and FR-FAC-03: set status to Inactive
        return updateStatus(id, FacilityStatus.Inactive);
    }

    /**
     * Updates only the status column of a facility (FR-FAC-03).
     *
     * @param facilityId The facility ID
     * @param newStatus  The new status
     * @return true if updated successfully
     */
    public boolean updateStatus(int facilityId, FacilityStatus newStatus) {
        String sql = "UPDATE facilities SET status = ? WHERE facility_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newStatus.getDbValue());
            pstmt.setInt(2, facilityId);

            int rows = pstmt.executeUpdate();
            if (rows > 0) {
                logger.info("Updated facility ID {} status to {}", facilityId, newStatus);
                return true;
            }

        } catch (SQLException e) {
            logger.error("Error updating facility status for ID {}: {}", facilityId, e.getMessage(), e);
        }
        return false;
    }

    /**
     * Checks if a facility with the same name already exists in the same building (FR-FAC-05).
     * Case-insensitive comparison.
     *
     * @param name              The facility name
     * @param building          The building name
     * @param excludeFacilityId ID to exclude (e.g. current ID when editing, 0 for new)
     * @return true if duplicate exists
     */
    public boolean isDuplicateNameInBuilding(String name, String building, int excludeFacilityId) {
        String sql = "SELECT COUNT(*) FROM facilities WHERE LOWER(name) = LOWER(?) AND LOWER(building) = LOWER(?) AND facility_id != ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name.trim());
            pstmt.setString(2, building.trim());
            pstmt.setInt(3, excludeFacilityId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }

        } catch (SQLException e) {
            logger.error("Error checking duplicate facility name: {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * Searches and filters facilities based on optional criteria (FR-FAC-04).
     *
     * @param keyword        Matches facility name or building (nullable)
     * @param type           Facility type (nullable)
     * @param building       Building name (nullable)
     * @param minCapacity    Minimum capacity (nullable)
     * @param amenityKeyword Amenity substring (nullable)
     * @param status         Status filter (nullable)
     * @return Filtered list of facilities
     */
    public List<Facility> searchAndFilter(String keyword, FacilityType type, String building,
                                          Integer minCapacity, String amenityKeyword,
                                          FacilityStatus status) {
        List<Facility> results = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM facilities WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            sql.append("AND (LOWER(name) LIKE ? OR LOWER(building) LIKE ?) ");
            String wild = "%" + keyword.trim().toLowerCase() + "%";
            params.add(wild);
            params.add(wild);
        }

        if (type != null) {
            sql.append("AND type = ? ");
            params.add(type.name());
        }

        if (building != null && !building.trim().isEmpty() && !building.equalsIgnoreCase("All")) {
            sql.append("AND LOWER(building) = LOWER(?) ");
            params.add(building.trim());
        }

        if (minCapacity != null && minCapacity > 0) {
            sql.append("AND capacity >= ? ");
            params.add(minCapacity);
        }

        if (amenityKeyword != null && !amenityKeyword.trim().isEmpty()) {
            sql.append("AND LOWER(amenities) LIKE ? ");
            params.add("%" + amenityKeyword.trim().toLowerCase() + "%");
        }

        if (status != null) {
            sql.append("AND status = ? ");
            params.add(status.getDbValue());
        }

        sql.append("ORDER BY building ASC, floor ASC, name ASC");

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                Object param = params.get(i);
                if (param instanceof String) {
                    pstmt.setString(i + 1, (String) param);
                } else if (param instanceof Integer) {
                    pstmt.setInt(i + 1, (Integer) param);
                }
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRowToFacility(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Error executing facility search and filter: {}", e.getMessage(), e);
        }
        return results;
    }

    /**
     * Gets all distinct building names for the filter dropdown.
     *
     * @return List of unique building names
     */
    public List<String> getDistinctBuildings() {
        List<String> buildings = new ArrayList<>();
        String sql = "SELECT DISTINCT building FROM facilities ORDER BY building ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                buildings.add(rs.getString("building"));
            }

        } catch (SQLException e) {
            logger.error("Error retrieving distinct buildings: {}", e.getMessage(), e);
        }
        return buildings;
    }

    /**
     * Counts future approved bookings for a facility (FR-FAC-06).
     *
     * @param facilityId The facility ID
     * @return Count of upcoming approved bookings
     */
    public int countFutureApprovedBookings(int facilityId) {
        String sql = "SELECT COUNT(*) FROM bookings " +
                     "WHERE facility_id = ? AND booking_date >= CURRENT_DATE AND status = 'Approved'";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, facilityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }

        } catch (SQLException e) {
            logger.error("Error counting future bookings for facility ID {}: {}", facilityId, e.getMessage(), e);
        }
        return 0;
    }

    /**
     * Cancels all future approved bookings for a deactivated facility and notifies affected users (FR-FAC-06).
     * Executes inside a database transaction for data consistency.
     *
     * @param facilityId The facility ID being deactivated
     * @param reason     Cancellation message for affected users
     * @return Number of bookings cancelled
     */
    public int cancelFutureBookingsAndNotify(int facilityId, String reason) {
        String selectBookingsSql = "SELECT booking_id, user_id, booking_date, slot_no FROM bookings " +
                                  "WHERE facility_id = ? AND booking_date >= CURRENT_DATE AND status = 'Approved'";

        String updateBookingSql = "UPDATE bookings SET status = 'Cancelled', active_flag = NULL WHERE booking_id = ?";

        String insertNotifSql = "INSERT INTO notifications (user_id, message, type, ref_key, is_read) " +
                                "VALUES (?, ?, 'FACILITY_DEACTIVATED', ?, FALSE)";

        int cancelledCount = 0;

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            conn.setAutoCommit(false);

            List<int[]> affected = new ArrayList<>();
            try (PreparedStatement pstmt = conn.prepareStatement(selectBookingsSql)) {
                pstmt.setInt(1, facilityId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        // [booking_id, user_id]
                        affected.add(new int[]{rs.getInt("booking_id"), rs.getInt("user_id")});
                    }
                }
            }

            // Cancel each booking and insert notification
            try (PreparedStatement updateStmt = conn.prepareStatement(updateBookingSql);
                 PreparedStatement notifStmt = conn.prepareStatement(insertNotifSql)) {

                for (int i = 0; i < affected.size(); i++) {
                    int bookingId = affected.get(i)[0];
                    int userId = affected.get(i)[1];

                    // 1. Cancel booking
                    updateStmt.setInt(1, bookingId);
                    updateStmt.executeUpdate();

                    // 2. Insert notification
                    notifStmt.setInt(1, userId);
                    notifStmt.setString(2, reason + " (Booking #" + bookingId + " cancelled)");
                    notifStmt.setString(3, "FACILITY_" + facilityId);
                    notifStmt.executeUpdate();

                    cancelledCount++;
                }

                conn.commit();
                logger.info("Cancelled {} future bookings for facility ID {}", cancelledCount, facilityId);

            } catch (SQLException ex) {
                conn.rollback();
                logger.error("Transaction failed during booking cancellation, rolled back: {}", ex.getMessage(), ex);
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            logger.error("Error cancelling bookings for facility ID {}: {}", facilityId, e.getMessage(), e);
        }

        return cancelledCount;
    }
}
