package com.fms.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes database tables, indexes, and seeds realistic reference and sample data.
 * Covers all modules: Users/Auth, Facilities, Equipment, Bookings, Maintenance, and Notifications.
 */
public class DatabaseInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    public static void initializeDatabase() {
        logger.info("Checking database schema and seed data...");
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            executeSchemaScript(conn);
            migrateBookingsTable(conn);
            seedTimeSlots(conn);

            // If database has no users, seed the complete rich dataset
            String checkUsersSql = "SELECT COUNT(*) FROM users";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(checkUsersSql)) {
                if (!rs.next() || rs.getInt(1) == 0) {
                    recreateAndSeedRichDatabase();
                    return;
                }
            }

            seedFacilities(conn);
            logger.info("Database initialized successfully.");
        } catch (Exception e) {
            logger.error("Database initialization failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    /**
     * Completely resets all tables and populates a rich, realistic showcase dataset.
     */
    public static void recreateAndSeedRichDatabase() {
        logger.info("Recreating and populating rich database showcase dataset...");
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                stmt.execute("DROP TABLE IF EXISTS audit_log");
                stmt.execute("DROP TABLE IF EXISTS notifications");
                stmt.execute("DROP TABLE IF EXISTS maintenance_log");
                stmt.execute("DROP TABLE IF EXISTS maintenance_requests");
                stmt.execute("DROP TABLE IF EXISTS bookings");
                stmt.execute("DROP TABLE IF EXISTS equipment");
                stmt.execute("DROP TABLE IF EXISTS facilities");
                stmt.execute("DROP TABLE IF EXISTS staff");
                stmt.execute("DROP TABLE IF EXISTS users");
                stmt.execute("DROP TABLE IF EXISTS time_slots");
                stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
            }

            executeSchemaScript(conn);
            migrateBookingsTable(conn);
            seedTimeSlots(conn);

            Map<String, Integer> userIds = seedUsersAndStaffRich(conn);
            Map<String, Integer> staffIds = fetchStaffIds(conn);
            Map<String, Integer> facilityIds = seedFacilitiesRich(conn);
            Map<String, Integer> equipmentIds = seedEquipmentRich(conn, facilityIds);
            seedBookingsRich(conn, userIds, facilityIds);
            seedMaintenanceRich(conn, userIds, staffIds, facilityIds, equipmentIds);
            seedNotificationsRich(conn, userIds);

            logger.info("Pristine showcase database seeded with 19 facilities, active equipment, bookings, maintenance, and users.");
        } catch (Exception e) {
            logger.error("Failed to recreate and seed rich database: {}", e.getMessage(), e);
            throw new RuntimeException("Database recreation failed", e);
        }
    }

    private static void executeSchemaScript(Connection conn) throws Exception {
        String schemaPath = "com/fms/db/schema.sql";
        try (InputStream in = DatabaseInitializer.class.getClassLoader().getResourceAsStream(schemaPath)) {
            if (in == null) {
                logger.warn("schema.sql not found on classpath: {}", schemaPath);
                return;
            }
            StringBuilder cleanSql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmedLine = line.trim();
                    if (!trimmedLine.startsWith("--")) {
                        cleanSql.append(line).append("\n");
                    }
                }
            }

            String[] statements = cleanSql.toString().split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty()) {
                        try {
                            stmt.execute(trimmed);
                        } catch (SQLException e) {
                            logger.warn("Schema statement note: {} -> {}", 
                                trimmed.substring(0, Math.min(50, trimmed.length())), e.getMessage());
                        }
                    }
                }
            }
            logger.info("Executed schema.sql successfully.");
        }
    }

    private static void migrateBookingsTable(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE bookings ADD COLUMN rejection_reason VARCHAR(255) NULL");
        } catch (SQLException ignored) {
            // Already present
        }

        try (Statement stmt = conn.createStatement()) {
            // FR-X1-02: bookings inside a maintenance window are flagged 'Affected'
            stmt.execute("ALTER TABLE bookings ADD COLUMN affected_by_maintenance BOOLEAN NOT NULL DEFAULT FALSE");
        } catch (SQLException ignored) {
            // Already present
        }
    }

    private static void seedTimeSlots(Connection conn) throws SQLException {
        String insertSql = "INSERT INTO time_slots (slot_no, start_time, end_time) VALUES (?, ?, ?) " +
                           "ON DUPLICATE KEY UPDATE start_time=VALUES(start_time), end_time=VALUES(end_time)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            for (int i = 1; i <= 10; i++) {
                int startHour = 7 + i; // Slot 1: 08:00, Slot 10: 17:00
                int endHour = startHour + 1;
                pstmt.setInt(1, i);
                pstmt.setTime(2, Time.valueOf(String.format("%02d:00:00", startHour)));
                pstmt.setTime(3, Time.valueOf(String.format("%02d:00:00", endHour)));
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            logger.info("Seeded 10 fixed time slots (08:00 to 18:00).");
        }
    }

    private static Map<String, Integer> seedUsersAndStaffRich(Connection conn) throws SQLException {
        Map<String, Integer> userIds = new HashMap<>();
        String insertUserSql = "INSERT INTO users (username, password_hash, role, full_name, email, is_active) " +
                               "VALUES (?, ?, ?, ?, ?, ?)";

        Object[][] users = {
            {"admin", "Admin@123", "Admin", "Dr. A. K. Verma", "admin@slotwise.edu"},
            {"tech_raj", "Staff@123", "Staff", "Rajesh Kumar", "rajesh.k@slotwise.edu"},
            {"tech_priya", "Staff@123", "Staff", "Priya Sundaram", "priya.s@slotwise.edu"},
            {"tech_suresh", "Staff@123", "Staff", "Suresh Nair", "suresh.n@slotwise.edu"},
            {"tech_anita", "Staff@123", "Staff", "Anita Desai", "anita.d@slotwise.edu"},
            {"harsita", "User@123", "User", "Harsita S. S.", "harsita@slotwise.edu"},
            {"dr_sharma", "User@123", "User", "Dr. Ramesh Sharma", "r.sharma@slotwise.edu"},
            {"prof_mehta", "User@123", "User", "Prof. Vikram Mehta", "v.mehta@slotwise.edu"}
        };

        try (PreparedStatement pstmt = conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS)) {
            for (Object[] u : users) {
                String username = (String) u[0];
                pstmt.setString(1, username);
                pstmt.setString(2, PasswordUtil.hashPassword((String) u[1]));
                pstmt.setString(3, (String) u[2]);
                pstmt.setString(4, (String) u[3]);
                pstmt.setString(5, (String) u[4]);
                pstmt.setBoolean(6, true);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        userIds.put(username, rs.getInt(1));
                    }
                }
            }
        }

        // Link Staff records
        String insertStaffSql = "INSERT INTO staff (user_id, specialization, phone, is_active) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertStaffSql)) {
            Object[][] staffDetails = {
                {userIds.get("tech_raj"), "Electrician", "+91 98765 43210"},
                {userIds.get("tech_priya"), "Lab Technician", "+91 98765 43211"},
                {userIds.get("tech_suresh"), "HVAC Specialist", "+91 98765 43212"},
                {userIds.get("tech_anita"), "IT Support", "+91 98765 43213"}
            };

            for (Object[] s : staffDetails) {
                pstmt.setInt(1, (Integer) s[0]);
                pstmt.setString(2, (String) s[1]);
                pstmt.setString(3, (String) s[2]);
                pstmt.setBoolean(4, true);
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            logger.info("Seeded 8 core users (Admin, 4 Staff, 3 Users) and linked staff specializations.");
        }
        return userIds;
    }

    private static Map<String, Integer> fetchStaffIds(Connection conn) throws SQLException {
        Map<String, Integer> staffMap = new HashMap<>();
        String sql = "SELECT s.staff_id, u.username FROM staff s JOIN users u ON s.user_id = u.user_id";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                staffMap.put(rs.getString("username"), rs.getInt("staff_id"));
            }
        }
        return staffMap;
    }

    private static Map<String, Integer> seedFacilitiesRich(Connection conn) throws SQLException {
        Map<String, Integer> facIds = new HashMap<>();
        String sql = "INSERT INTO facilities (name, type, building, floor, capacity, amenities, requires_approval, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        Object[][] facilities = {
            // Auditoriums & Conference (A, D, F Blocks)
            {"A-101 Main Auditorium", "Hall", "A-Block", 1, 600, "Stage, Central AC, Pro Audio, Projector", true, "Available"},
            {"A-201 Executive Conference Room", "Hall", "A-Block", 2, 40, "Conference Table, Smart Display, AC", true, "Available"},
            {"D-101 Seminar Hall D", "Hall", "D-Block", 1, 200, "Tiered Seating, Audio System, AC", true, "Available"},
            {"F-101 Amphitheater & Audit", "Hall", "F-Block", 1, 500, "Large Stage, Dolby Sound, Central AC", true, "Available"},
            {"F-201 Conference Suite F", "Hall", "F-Block", 2, 50, "Presentation Screen, Smart Board, AC", true, "Available"},

            // Specialized Labs (J & M Blocks)
            {"J-201 Advanced Computing Lab", "Laboratory", "J-Block", 2, 60, "60 Workstations, LAN, AC, Projector", true, "Available"},
            {"J-301 AI & Machine Learning Lab", "Laboratory", "J-Block", 3, 60, "High-End GPU Workstations, AC, LAN", true, "Available"},
            {"M-201 Embedded Systems Lab", "Laboratory", "M-Block", 2, 50, "Oscilloscopes, Microcontrollers, AC", true, "Available"},
            {"M-301 VLSI & Hardware Lab", "Laboratory", "M-Block", 3, 45, "Workstations, EDA Tools, AC, LAN", true, "Under Maintenance"},
            {"K-201 CAD Design Studio", "Laboratory", "K-Block", 2, 40, "Workstations, Plotters, AC, Projector", true, "Available"},

            // Classrooms & Lecture Halls across all blocks
            {"A-202 Lecture Classroom A", "Classroom", "A-Block", 2, 60, "Projector, Whiteboard, Wi-Fi", false, "Available"},
            {"D-202 Classroom D-202", "Classroom", "D-Block", 2, 70, "Projector, Whiteboard, Wi-Fi", false, "Available"},
            {"E-201 Smart Classroom E-201", "Classroom", "E-Block", 2, 75, "Projector, Smart Board, Wi-Fi, AC", false, "Available"},
            {"G-201 Lecture Hall G-201", "Classroom", "G-Block", 2, 120, "Tiered Seating, Projector, Audio, Wi-Fi", false, "Available"},
            {"H-201 Classroom H-201", "Classroom", "H-Block", 2, 60, "Projector, Whiteboard, Wi-Fi", false, "Available"},
            {"I-201 Classroom I-201", "Classroom", "I-Block", 2, 60, "Projector, Whiteboard, Wi-Fi", false, "Available"},
            {"N-201 Classroom N-201", "Classroom", "N-Block", 2, 70, "Projector, Wi-Fi, Ergonomic Desks", false, "Available"},
            {"Q-201 Lecture Theater Q-201", "Classroom", "Q-Block", 2, 100, "Tiered Seating, Dual Projectors, AC", false, "Available"},
            {"Y-201 Research Studio Y-201", "Hall", "Y-Block", 2, 35, "Discussion Tables, Smart Display, AC", true, "Available"}
        };

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (Object[] f : facilities) {
                String name = (String) f[0];
                pstmt.setString(1, name);
                pstmt.setString(2, (String) f[1]);
                pstmt.setString(3, (String) f[2]);
                pstmt.setInt(4, (Integer) f[3]);
                pstmt.setInt(5, (Integer) f[4]);
                pstmt.setString(6, (String) f[5]);
                pstmt.setBoolean(7, (Boolean) f[6]);
                pstmt.setString(8, (String) f[7]);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        facIds.put(name, rs.getInt(1));
                    }
                }
            }
            logger.info("Seeded exactly 19 core facilities across Halls, Labs, and Classrooms.");
        }
        return facIds;
    }

    private static Map<String, Integer> seedEquipmentRich(Connection conn, Map<String, Integer> facilityIds) throws SQLException {
        Map<String, Integer> equipMap = new HashMap<>();
        String sql = "INSERT INTO equipment (facility_id, name, category, serial_no, purchase_date, condition_status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";

        Object[][] items = {
            {facilityIds.get("A-101 Main Auditorium"), "Christie 4K Laser Cinema Projector", "Audio-Visual", "PRJ-CHR-4K-101", "2023-08-10", "Good"},
            {facilityIds.get("A-101 Main Auditorium"), "Yamaha CL5 72-Channel Digital Mixing Console", "Audio", "AUD-YMH-CL5-01", "2023-08-10", "Good"},
            {facilityIds.get("A-101 Main Auditorium"), "Shure Axient Digital Wireless Mic System", "Audio", "MIC-SHR-AD4D-01", "2023-09-01", "Good"},
            {facilityIds.get("A-201 Executive Conference Room"), "Samsung Flip Pro 85\" 4K Interactive Display", "Display", "DSP-SAM-85-201", "2024-01-12", "Good"},
            {facilityIds.get("A-201 Executive Conference Room"), "Polycom Studio X50 Video Conference Bar", "Audio-Visual", "VC-PLY-X50-201", "2024-01-12", "Good"},
            {facilityIds.get("F-101 Amphitheater & Audit"), "Daikin Multi-Split Industrial HVAC Inverter", "HVAC", "HVAC-DKN-F101-01", "2021-05-18", "Under Repair"},
            {facilityIds.get("J-301 AI & Machine Learning Lab"), "Dell Precision 7960 Tower (Dual RTX A6000)", "Computing", "WS-DLL-A6000-01", "2024-03-01", "Good"},
            {facilityIds.get("J-301 AI & Machine Learning Lab"), "Cisco Catalyst 9300 48-Port Gigabit Switch", "Networking", "NET-CSCO-9300-301", "2024-02-15", "Good"},
            {facilityIds.get("M-201 Embedded Systems Lab"), "Keysight InfiniiVision 4-Ch Digital Oscilloscope", "Laboratory", "OSC-KYS-4000-01", "2023-11-20", "Good"},
            {facilityIds.get("M-301 VLSI & Hardware Lab"), "Advantest Mixed-Signal IC Test Station", "Laboratory", "TST-ADV-IC-301", "2022-07-15", "Needs Repair"},
            {facilityIds.get("D-202 Classroom D-202"), "Sony VPL-FHZ85 Laser Projector", "Audio-Visual", "PRJ-SNY-FHZ-202", "2022-04-10", "Needs Repair"},
            {facilityIds.get("E-201 Smart Classroom E-201"), "BenQ Board Pro 75\" Smart Interactive Whiteboard", "Display", "WBD-BNQ-75-201", "2024-01-05", "Good"}
        };

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (Object[] item : items) {
                Integer facId = (Integer) item[0];
                if (facId == null) continue;
                String serial = (String) item[3];
                pstmt.setInt(1, facId);
                pstmt.setString(2, (String) item[1]);
                pstmt.setString(3, (String) item[2]);
                pstmt.setString(4, serial);
                pstmt.setDate(5, Date.valueOf((String) item[4]));
                pstmt.setString(6, (String) item[5]);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        equipMap.put(serial, rs.getInt(1));
                    }
                }
            }
            logger.info("Seeded 12 high-value equipment assets linked to respective rooms.");
        }
        return equipMap;
    }

    private static void seedBookingsRich(Connection conn, Map<String, Integer> userIds, Map<String, Integer> facilityIds) throws SQLException {
        String sql = "INSERT INTO bookings (facility_id, user_id, booking_date, slot_no, purpose, attendees, status, rejection_reason, active_flag) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);
        LocalDate yesterday = today.minusDays(1);

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 1. Today: Approved lecture by harsita in A-202 (Slots 2 & 3: 09:00 - 11:00)
            int facA202 = facilityIds.get("A-202 Lecture Classroom A");
            int userHarsita = userIds.get("harsita");
            for (int slot : new int[]{2, 3}) {
                addBookingBatch(pstmt, facA202, userHarsita, today, slot, "Data Structures & Algorithms Practical Lecture", 45, "Approved", null, 1);
            }

            // 2. Today: Approved guest lecture by dr_sharma in D-101 (Slots 6, 7 & 8: 13:00 - 16:00)
            int facD101 = facilityIds.get("D-101 Seminar Hall D");
            int userSharma = userIds.get("dr_sharma");
            for (int slot : new int[]{6, 7, 8}) {
                addBookingBatch(pstmt, facD101, userSharma, today, slot, "Quantum Computing Systems Guest Lecture", 120, "Approved", null, 1);
            }

            // 3. Tomorrow: Pending Full-Day reservation by harsita in A-101 (Slots 1 to 10: 08:00 - 18:00)
            int facA101 = facilityIds.get("A-101 Main Auditorium");
            for (int slot = 1; slot <= 10; slot++) {
                addBookingBatch(pstmt, facA101, userHarsita, tomorrow, slot, "SlotWise Inter-College Tech Symposium & Hackathon", 400, "Pending", null, 1);
            }

            // 4. Tomorrow: Pending AI workshop by prof_mehta in J-301 (Slots 3, 4, 5: 10:00 - 13:00)
            int facJ301 = facilityIds.get("J-301 AI & Machine Learning Lab");
            int userMehta = userIds.get("prof_mehta");
            for (int slot : new int[]{3, 4, 5}) {
                addBookingBatch(pstmt, facJ301, userMehta, tomorrow, slot, "Deep Learning GPU Model Acceleration Workshop", 55, "Pending", null, 1);
            }

            // 5. Tomorrow: Approved Robotics Club session in E-201 (Slots 2 & 3: 09:00 - 11:00)
            int facE201 = facilityIds.get("E-201 Smart Classroom E-201");
            for (int slot : new int[]{2, 3}) {
                addBookingBatch(pstmt, facE201, userHarsita, tomorrow, slot, "Autonomous Robotics Sprint Review", 30, "Approved", null, 1);
            }

            // 6. Yesterday: Completed lab exam in J-201 by prof_mehta (Slots 2, 3, 4)
            int facJ201 = facilityIds.get("J-201 Advanced Computing Lab");
            for (int slot : new int[]{2, 3, 4}) {
                addBookingBatch(pstmt, facJ201, userMehta, yesterday, slot, "Distributed Cloud Architecture Lab Exam", 58, "Completed", null, 1);
            }

            // 7. Yesterday: Rejected booking in A-201 by harsita (Slot 9)
            int facA201 = facilityIds.get("A-201 Executive Conference Room");
            addBookingBatch(pstmt, facA201, userHarsita, yesterday, 9, "Student Welfare Committee Budget Review", 15, "Rejected", "Room reserved for Board of Governors Session", null);

            pstmt.executeBatch();
            logger.info("Seeded live multi-slot bookings (Pending, Approved, Completed, Rejected).");
        }
    }

    private static void addBookingBatch(PreparedStatement pstmt, int facilityId, int userId, LocalDate date,
                                        int slotNo, String purpose, int attendees, String status,
                                        String rejectionReason, Integer activeFlag) throws SQLException {
        pstmt.setInt(1, facilityId);
        pstmt.setInt(2, userId);
        pstmt.setDate(3, Date.valueOf(date));
        pstmt.setInt(4, slotNo);
        pstmt.setString(5, purpose);
        pstmt.setInt(6, attendees);
        pstmt.setString(7, status);
        if (rejectionReason != null) {
            pstmt.setString(8, rejectionReason);
        } else {
            pstmt.setNull(8, Types.VARCHAR);
        }
        if (activeFlag != null) {
            pstmt.setInt(9, activeFlag);
        } else {
            pstmt.setNull(9, Types.TINYINT);
        }
        pstmt.addBatch();
    }

    private static void seedMaintenanceRich(Connection conn, Map<String, Integer> userIds, Map<String, Integer> staffIds,
                                            Map<String, Integer> facilityIds, Map<String, Integer> equipmentIds) throws SQLException {
        String sql = "INSERT INTO maintenance_requests (facility_id, equipment_id, raised_by, assigned_staff, description, priority, status, scheduled_date, start_slot, end_slot) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // Ticket 1: In Progress - Sony Projector in D-202
            pstmt.setInt(1, facilityIds.get("D-202 Classroom D-202"));
            pstmt.setInt(2, equipmentIds.get("PRJ-SNY-FHZ-202"));
            pstmt.setInt(3, userIds.get("harsita"));
            pstmt.setInt(4, staffIds.get("tech_anita"));
            pstmt.setString(5, "Projector lamp overheated and shuts off intermittently every 15 minutes during lectures.");
            pstmt.setString(6, "High");
            pstmt.setString(7, "In Progress");
            pstmt.setDate(8, Date.valueOf(today));
            pstmt.setInt(9, 4);
            pstmt.setInt(10, 5);
            pstmt.executeUpdate();

            // Ticket 2: Assigned - Daikin HVAC in F-101
            pstmt.setInt(1, facilityIds.get("F-101 Amphitheater & Audit"));
            pstmt.setInt(2, equipmentIds.get("HVAC-DKN-F101-01"));
            pstmt.setInt(3, userIds.get("dr_sharma"));
            pstmt.setInt(4, staffIds.get("tech_suresh"));
            pstmt.setString(5, "Central AC cooling cycle tripped; temperature inside amphitheater reaching 32°C.");
            pstmt.setString(6, "Urgent");
            pstmt.setString(7, "Assigned");
            pstmt.setDate(8, Date.valueOf(tomorrow));
            pstmt.setInt(9, 1);
            pstmt.setInt(10, 4);
            pstmt.executeUpdate();

            // Ticket 3: Reported (Unassigned) - Advantest IC Station in M-301
            pstmt.setInt(1, facilityIds.get("M-301 VLSI & Hardware Lab"));
            pstmt.setInt(2, equipmentIds.get("TST-ADV-IC-301"));
            pstmt.setInt(3, userIds.get("prof_mehta"));
            pstmt.setNull(4, Types.INTEGER);
            pstmt.setString(5, "Calibration error 0x4B on pin probing channel 3 and 4 during wafer testing.");
            pstmt.setString(6, "Medium");
            pstmt.setString(7, "Reported");
            pstmt.setDate(8, Date.valueOf(tomorrow));
            pstmt.setNull(9, Types.INTEGER);
            pstmt.setNull(10, Types.INTEGER);
            pstmt.executeUpdate();

            // Ticket 4: Completed - Yamaha Console in A-101
            pstmt.setInt(1, facilityIds.get("A-101 Main Auditorium"));
            pstmt.setInt(2, equipmentIds.get("AUD-YMH-CL5-01"));
            pstmt.setInt(3, userIds.get("admin"));
            pstmt.setInt(4, staffIds.get("tech_raj"));
            pstmt.setString(5, "Stage monitor ground loop hum on stage-right active wedge.");
            pstmt.setString(6, "Low");
            pstmt.setString(7, "Completed");
            pstmt.setDate(8, Date.valueOf(today.minusDays(1)));
            pstmt.setInt(9, 2);
            pstmt.setInt(10, 3);
            pstmt.executeUpdate();

            logger.info("Seeded 4 realistic maintenance dispatch tickets (Reported, Assigned, In Progress, Completed).");
        }
    }

    private static void seedNotificationsRich(Connection conn, Map<String, Integer> userIds) throws SQLException {
        String sql = "INSERT INTO notifications (user_id, message, type, is_read) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Admin notifications
            pstmt.setInt(1, userIds.get("admin"));
            pstmt.setString(2, "New reservation pending review: 'SlotWise Annual Tech Symposium' in A-101 Main Auditorium for tomorrow.");
            pstmt.setString(3, "BOOKING_PENDING");
            pstmt.setBoolean(4, false);
            pstmt.addBatch();

            pstmt.setInt(1, userIds.get("admin"));
            pstmt.setString(2, "Urgent maintenance ticket logged for F-101 Amphitheater HVAC by Dr. Ramesh Sharma.");
            pstmt.setString(3, "MAINTENANCE_URGENT");
            pstmt.setBoolean(4, false);
            pstmt.addBatch();

            // Harsita notifications
            pstmt.setInt(1, userIds.get("harsita"));
            pstmt.setString(2, "Your reservation for 'A-202 Lecture Classroom A' (Today, 09:00 - 11:00) has been confirmed.");
            pstmt.setString(3, "BOOKING_APPROVED");
            pstmt.setBoolean(4, true);
            pstmt.addBatch();

            // Prof Mehta notification
            pstmt.setInt(1, userIds.get("prof_mehta"));
            pstmt.setString(2, "Booking for 'J-201 Advanced Computing Lab' yesterday was marked Completed.");
            pstmt.setString(3, "BOOKING_COMPLETED");
            pstmt.setBoolean(4, true);
            pstmt.addBatch();

            pstmt.executeBatch();
            logger.info("Seeded user and admin inbox notifications.");
        }
    }

    private static void seedFacilities(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM facilities")) {
            if (rs.next() && rs.getInt(1) > 0) {
                return; // facilities already seeded, do nothing
            }
        }
        seedFacilitiesRich(conn);
    }
}
