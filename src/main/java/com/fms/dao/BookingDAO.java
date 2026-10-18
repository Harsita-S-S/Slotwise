package com.fms.dao;

import com.fms.model.Booking;
import com.fms.model.BookingStatus;
import com.fms.model.FacilityType;
import com.fms.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Access Object for bookings and slot availability in MySQL.
 * Enforces BR-01, Section 7.3 Concurrency Design, and transactional safety.
 */
public class BookingDAO {
    private static final Logger logger = LoggerFactory.getLogger(BookingDAO.class);

    /**
     * Inserts multiple bookings in a single transaction.
     * All succeed or all fail together (BR-03, Section 7.3).
     *
     * @param bookings List of booking objects (1 to 3 slots)
     * @param conn Active connection with setAutoCommit(false)
     * @return List of created bookings with generated IDs
     * @throws SQLException on error or duplicate slot conflict
     */
    public List<Booking> createBookings(List<Booking> bookings, Connection conn) throws SQLException {
        String sql = "INSERT INTO bookings (facility_id, user_id, booking_date, slot_no, purpose, attendees, " +
                     "status, rejection_reason, active_flag) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (Booking b : bookings) {
                pstmt.setInt(1, b.getFacilityId());
                pstmt.setInt(2, b.getUserId());
                pstmt.setDate(3, Date.valueOf(b.getBookingDate()));
                pstmt.setInt(4, b.getSlotNo());
                pstmt.setString(5, b.getPurpose());
                pstmt.setInt(6, b.getAttendees());
                pstmt.setString(7, b.getStatus().name());
                pstmt.setString(8, b.getRejectionReason());
                if (b.getActiveFlag() != null) {
                    pstmt.setInt(9, b.getActiveFlag());
                } else {
                    pstmt.setNull(9, Types.TINYINT);
                }
                pstmt.addBatch();
            }

            pstmt.executeBatch();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                int index = 0;
                while (rs.next() && index < bookings.size()) {
                    bookings.get(index).setBookingId(rs.getInt(1));
                    index++;
                }
            }
        }
        return bookings;
    }

    /**
     * Checks if a clashing active booking exists for a facility, date, and slot using SELECT ... FOR UPDATE (Sec 7.3).
     *
     * @param facilityId target room
     * @param date target date
     * @param slotNo target slot
     * @param conn active transaction connection
     * @return Optional containing the clashing booking if found
     */
    public Optional<Booking> findActiveBookingForUpdate(int facilityId, LocalDate date, int slotNo, Connection conn) throws SQLException {
        String sql = "SELECT booking_id, facility_id, user_id, booking_date, slot_no, purpose, attendees, status " +
                     "FROM bookings WHERE facility_id = ? AND booking_date = ? AND slot_no = ? AND active_flag = 1 FOR UPDATE";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, facilityId);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setInt(3, slotNo);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Booking b = new Booking();
                    b.setBookingId(rs.getInt("booking_id"));
                    b.setFacilityId(rs.getInt("facility_id"));
                    b.setUserId(rs.getInt("user_id"));
                    b.setBookingDate(rs.getDate("booking_date").toLocalDate());
                    b.setSlotNo(rs.getInt("slot_no"));
                    b.setPurpose(rs.getString("purpose"));
                    b.setAttendees(rs.getInt("attendees"));
                    b.setStatus(BookingStatus.valueOf(rs.getString("status")));
                    return Optional.of(b);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks if a facility is under a scheduled maintenance window during a slot (BR-06).
     */
    public boolean isSlotUnderMaintenance(int facilityId, LocalDate date, int slotNo) {
        String sql = "SELECT COUNT(*) FROM maintenance_requests " +
                     "WHERE facility_id = ? AND scheduled_date = ? " +
                     "AND status IN ('Assigned', 'In Progress') " +
                     "AND start_slot <= ? AND end_slot >= ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, facilityId);
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setInt(3, slotNo);
            pstmt.setInt(4, slotNo);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.warn("Could not check maintenance schedule for facility {} on {}: {}", facilityId, date, e.getMessage());
        }
        return false;
    }

    /**
     * Finds all active bookings for a facility on a specific date.
     */
    public Map<Integer, Booking> getActiveBookingsForFacilityAndDate(int facilityId, LocalDate date) {
        Map<Integer, Booking> map = new HashMap<>();
        String sql = "SELECT b.*, u.username, u.full_name " +
                     "FROM bookings b " +
                     "JOIN users u ON b.user_id = u.user_id " +
                     "WHERE b.facility_id = ? AND b.booking_date = ? AND b.active_flag = 1";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, facilityId);
            pstmt.setDate(2, Date.valueOf(date));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Booking b = mapRowToBooking(rs);
                    map.put(b.getSlotNo(), b);
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying active bookings for facility {} on {}: {}", facilityId, date, e.getMessage());
        }
        return map;
    }

    /**
     * Finds bookings created by a specific user (upcoming and past) (FR-BKG-08).
     */
    public List<Booking> findBookingsByUser(int userId) {
        List<Booking> list = new ArrayList<>();
        String sql = "SELECT b.*, f.name AS facility_name, f.building AS facility_building, f.type AS facility_type, " +
                     "f.capacity AS facility_capacity, u.username, u.full_name " +
                     "FROM bookings b " +
                     "JOIN facilities f ON b.facility_id = f.facility_id " +
                     "JOIN users u ON b.user_id = u.user_id " +
                     "WHERE b.user_id = ? " +
                     "ORDER BY b.booking_date DESC, b.slot_no ASC";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToBooking(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error querying user bookings for user ID {}: {}", userId, e.getMessage());
        }
        return list;
    }

    /**
     * Admin query to view and filter all bookings (FR-BKG-09).
     */
    public List<Booking> searchBookings(LocalDate date, Integer facilityId, Integer userId, BookingStatus status) {
        List<Booking> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
            "SELECT b.*, f.name AS facility_name, f.building AS facility_building, f.type AS facility_type, " +
            "f.capacity AS facility_capacity, u.username, u.full_name " +
            "FROM bookings b " +
            "JOIN facilities f ON b.facility_id = f.facility_id " +
            "JOIN users u ON b.user_id = u.user_id " +
            "WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();
        if (date != null) {
            sql.append("AND b.booking_date = ? ");
            params.add(Date.valueOf(date));
        }
        if (facilityId != null && facilityId > 0) {
            sql.append("AND b.facility_id = ? ");
            params.add(facilityId);
        }
        if (userId != null && userId > 0) {
            sql.append("AND b.user_id = ? ");
            params.add(userId);
        }
        if (status != null) {
            sql.append("AND b.status = ? ");
            params.add(status.name());
        }

        sql.append("ORDER BY b.booking_date DESC, b.slot_no ASC");

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                pstmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToBooking(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching bookings: {}", e.getMessage());
        }
        return list;
    }

    /**
     * Finds a booking by ID.
     */
    public Optional<Booking> findById(int bookingId) {
        String sql = "SELECT b.*, f.name AS facility_name, f.building AS facility_building, f.type AS facility_type, " +
                     "f.capacity AS facility_capacity, u.username, u.full_name " +
                     "FROM bookings b " +
                     "JOIN facilities f ON b.facility_id = f.facility_id " +
                     "JOIN users u ON b.user_id = u.user_id " +
                     "WHERE b.booking_id = ?";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, bookingId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToBooking(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Error finding booking ID {}: {}", bookingId, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Updates status, rejection reason, and active flag for a booking.
     */
    public boolean updateStatus(int bookingId, BookingStatus newStatus, String rejectionReason, Integer activeFlag, Connection conn) throws SQLException {
        String sql = "UPDATE bookings SET status = ?, rejection_reason = ?, active_flag = ? WHERE booking_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus.name());
            pstmt.setString(2, rejectionReason);
            if (activeFlag != null) {
                pstmt.setInt(3, activeFlag);
            } else {
                pstmt.setNull(3, Types.TINYINT);
            }
            pstmt.setInt(4, bookingId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Cancels a booking, freeing its slot for rebooking (BR-05, Sec 7.3).
     */
    public boolean cancelBooking(int bookingId, Connection conn) throws SQLException {
        return updateStatus(bookingId, BookingStatus.Cancelled, null, null, conn);
    }

    /**
     * Marks passed approved bookings as Completed (FR-BKG-10, Sec 7.3).
     */
    public int markPassedApprovedBookingsCompleted(LocalDate currentDate, int currentSlotNo) {
        String completeSql = "UPDATE bookings SET status = 'Completed', active_flag = NULL " +
                     "WHERE status = 'Approved' AND active_flag = 1 " +
                     "AND (booking_date < ? OR (booking_date = ? AND slot_no < ?))";
        String expireSql = "UPDATE bookings SET status = 'Rejected', " +
                     "rejection_reason = 'Not approved before the slot started', active_flag = NULL " +
                     "WHERE status = 'Pending' AND active_flag = 1 " +
                     "AND (booking_date < ? OR (booking_date = ? AND slot_no <= ?))";

        try (Connection conn = DBConnection.getInstance().getConnection()) {
            int completed = runPassedUpdate(conn, completeSql, currentDate, currentSlotNo);
            int expired = runPassedUpdate(conn, expireSql, currentDate, currentSlotNo);
            if (completed > 0) {
                logger.info("Auto-completed {} passed approved bookings.", completed);
            }
            if (expired > 0) {
                logger.info("Auto-rejected {} pending bookings whose slot started.", expired);
            }
            return completed + expired;
        } catch (SQLException e) {
            logger.error("Error updating passed bookings: {}", e.getMessage());
            return 0;
        }
    }

    private int runPassedUpdate(Connection conn, String sql, LocalDate date, int slotNo) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, Date.valueOf(date));
            pstmt.setDate(2, Date.valueOf(date));
            pstmt.setInt(3, slotNo);
            return pstmt.executeUpdate();
        }
    }
    

    /**
     * Returns the count of active bookings for today (all statuses except Cancelled).
     * Used by the Home Dashboard stat card (FR-BKG-08).
     */
    public int countTodayActiveBookings() {
        String sql = "SELECT COUNT(*) FROM bookings " +
             "WHERE booking_date = CURDATE() AND status IN ('Pending', 'Approved', 'Completed')";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Error counting today's bookings: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Returns the count of all bookings in 'Pending' status.
     * Used by the Home Dashboard "Pending Approvals" stat card.
     */
    public int countPendingApprovals() {
        String sql = "SELECT COUNT(*) FROM bookings WHERE status = 'Pending' AND active_flag = 1";
        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            logger.error("Error counting pending approvals: {}", e.getMessage());
        }
        return 0;
    }

    /**
     * Checks if a facility has upcoming approved bookings (used by FacilityService).
     */
    public boolean hasUpcomingApprovedBookings(int facilityId) {

        String sql = "SELECT COUNT(*) FROM bookings " +
                     "WHERE facility_id = ? AND status = 'Approved' AND active_flag = 1 " +
                     "AND booking_date >= CURDATE()";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, facilityId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            logger.error("Error checking upcoming bookings for facility {}: {}", facilityId, e.getMessage());
        }
        return false;
    }

    private Booking mapRowToBooking(ResultSet rs) throws SQLException {
        Booking b = new Booking();
        b.setBookingId(rs.getInt("booking_id"));
        b.setFacilityId(rs.getInt("facility_id"));
        b.setUserId(rs.getInt("user_id"));
        b.setBookingDate(rs.getDate("booking_date").toLocalDate());
        b.setSlotNo(rs.getInt("slot_no"));
        b.setPurpose(rs.getString("purpose"));
        b.setAttendees(rs.getInt("attendees"));
        b.setStatus(BookingStatus.valueOf(rs.getString("status")));

        try {
            b.setRejectionReason(rs.getString("rejection_reason"));
        } catch (SQLException ignored) {}

        int active = rs.getInt("active_flag");
        if (!rs.wasNull()) {
            b.setActiveFlag(active);
        } else {
            b.setActiveFlag(null);
        }

        Timestamp createdTs = rs.getTimestamp("created_at");
        if (createdTs != null) {
            b.setCreatedAt(createdTs.toLocalDateTime());
        }

        // Optional joined fields
        try {
            b.setFacilityName(rs.getString("facility_name"));
            b.setFacilityBuilding(rs.getString("facility_building"));
            b.setFacilityType(FacilityType.valueOf(rs.getString("facility_type")));
            b.setFacilityCapacity(rs.getInt("facility_capacity"));
        } catch (SQLException ignored) {}

        try {
            b.setUsername(rs.getString("username"));
            b.setUserFullName(rs.getString("full_name"));
        } catch (SQLException ignored) {}

        return b;
    }
}
