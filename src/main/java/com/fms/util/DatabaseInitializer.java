package com.fms.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initializes database tables, indexes, and seeds mandatory reference and sample data.
 * Adheres to:
 * - SRS Section 9 & 12 (Phase 1 Setup: DB schema, connection utility, sample data).
 * - Immediate Database Setup Actions (Items 1-7).
 */
public class DatabaseInitializer {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    public static void initializeDatabase() {
        logger.info("Starting database schema and seed data initialization...");
        try (Connection conn = DBConnection.getInstance().getConnection()) {
            executeSchemaScript(conn);
            seedTimeSlots(conn);
            seedUsersAndStaff(conn);
            seedFacilities(conn);
            seedEquipment(conn);
            logger.info("Database schema and seed data initialized successfully.");
        } catch (Exception e) {
            logger.error("Database initialization failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize database", e);
        }
    }

    private static void executeSchemaScript(Connection conn) throws Exception {
        String schemaPath = "com/fms/db/schema.sql";
        try (InputStream in = DatabaseInitializer.class.getClassLoader().getResourceAsStream(schemaPath)) {
            if (in == null) {
                logger.warn("schema.sql not found on classpath: {}", schemaPath);
                return;
            }
            String content = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));

            // Split statements by semicolon while ignoring comments
            String[] statements = content.split(";");
            try (Statement stmt = conn.createStatement()) {
                for (String sql : statements) {
                    String trimmed = sql.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("--")) {
                        try {
                            stmt.execute(trimmed);
                        } catch (SQLException e) {
                            logger.warn("Schema statement execution note (might already exist): {} -> {}", 
                                trimmed.substring(0, Math.min(50, trimmed.length())), e.getMessage());
                        }
                    }
                }
            }
            logger.info("Executed schema.sql successfully.");
        }
    }

    private static void seedTimeSlots(Connection conn) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM time_slots";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(countSql)) {
            if (rs.next() && rs.getInt(1) >= 10) {
                logger.info("Time slots already seeded (10 slots present).");
                return;
            }
        }

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

    private static void seedUsersAndStaff(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM users";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                logger.info("Users table already contains records. Skipping user seeding.");
                return;
            }
        }

        String insertUserSql = "INSERT INTO users (username, password_hash, role, full_name, email, is_active) " +
                               "VALUES (?, ?, ?, ?, ?, ?)";
        int adminId, staffRajId, staffPriyaId;

        try (PreparedStatement pstmt = conn.prepareStatement(insertUserSql, Statement.RETURN_GENERATED_KEYS)) {
            // Admin user
            pstmt.setString(1, "admin");
            pstmt.setString(2, PasswordUtil.hashPassword("Admin@123"));
            pstmt.setString(3, "Admin");
            pstmt.setString(4, "System Administrator");
            pstmt.setString(5, "admin@fms.edu");
            pstmt.setBoolean(6, true);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                keys.next();
                adminId = keys.getInt(1);
            }

            // Staff user 1 (Electrician)
            pstmt.setString(1, "tech_raj");
            pstmt.setString(2, PasswordUtil.hashPassword("Staff@123"));
            pstmt.setString(3, "Staff");
            pstmt.setString(4, "Rajesh Kumar");
            pstmt.setString(5, "rajesh.staff@fms.edu");
            pstmt.setBoolean(6, true);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                keys.next();
                staffRajId = keys.getInt(1);
            }

            // Staff user 2 (Lab Technician)
            pstmt.setString(1, "tech_priya");
            pstmt.setString(2, PasswordUtil.hashPassword("Staff@123"));
            pstmt.setString(3, "Staff");
            pstmt.setString(4, "Priya Sharma");
            pstmt.setString(5, "priya.staff@fms.edu");
            pstmt.setBoolean(6, true);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                keys.next();
                staffPriyaId = keys.getInt(1);
            }

            // Regular User 1 (Student/Faculty)
            pstmt.setString(1, "harsita");
            pstmt.setString(2, PasswordUtil.hashPassword("User@123"));
            pstmt.setString(3, "User");
            pstmt.setString(4, "Harsita S S");
            pstmt.setString(5, "harsita@fms.edu");
            pstmt.setBoolean(6, true);
            pstmt.executeUpdate();

            // Regular User 2 (Student/Faculty)
            pstmt.setString(1, "sunanda");
            pstmt.setString(2, PasswordUtil.hashPassword("User@123"));
            pstmt.setString(3, "User");
            pstmt.setString(4, "Sunanda H S");
            pstmt.setString(5, "sunanda@fms.edu");
            pstmt.setBoolean(6, true);
            pstmt.executeUpdate();
        }

        // Link Staff records (FR-STF-01, FR-STF-02)
        String insertStaffSql = "INSERT INTO staff (user_id, specialization, phone, is_active) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertStaffSql)) {
            // Rajesh - Electrician
            pstmt.setInt(1, staffRajId);
            pstmt.setString(2, "Electrician");
            pstmt.setString(3, "+91 9876543210");
            pstmt.setBoolean(4, true);
            pstmt.addBatch();

            // Priya - Lab Technician
            pstmt.setInt(1, staffPriyaId);
            pstmt.setString(2, "Lab Technician");
            pstmt.setString(3, "+91 9876543211");
            pstmt.setBoolean(4, true);
            pstmt.addBatch();

            pstmt.executeBatch();
            logger.info("Seeded initial users (Admin, Staff, Users) with valid BCrypt hashes and linked Staff profiles.");
        }
    }

    private static void seedFacilities(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM facilities";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                logger.info("Facilities table already contains records. Skipping facility seeding.");
                return;
            }
        }

        String sql = "INSERT INTO facilities (name, type, building, floor, capacity, amenities, requires_approval, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // 1. Classroom
            pstmt.setString(1, "Lecture Hall 101");
            pstmt.setString(2, "Classroom");
            pstmt.setString(3, "Academic Block A");
            pstmt.setInt(4, 1);
            pstmt.setInt(5, 50);
            pstmt.setString(6, "Epson Projector, Whiteboard, Wi-Fi, AC");
            pstmt.setBoolean(7, false);
            pstmt.setString(8, "Available");
            pstmt.addBatch();

            // 2. Laboratory
            pstmt.setString(1, "Advanced Computing Lab");
            pstmt.setString(2, "Laboratory");
            pstmt.setString(3, "Tech Block");
            pstmt.setInt(4, 2);
            pstmt.setInt(5, 60);
            pstmt.setString(6, "60 Dell Workstations, Gigabit LAN, AC, Smart Board");
            pstmt.setBoolean(7, true);
            pstmt.setString(8, "Available");
            pstmt.addBatch();

            // 3. Auditorium / Hall
            pstmt.setString(1, "Main Auditorium");
            pstmt.setString(2, "Hall");
            pstmt.setString(3, "Central Block");
            pstmt.setInt(4, 1);
            pstmt.setInt(5, 300);
            pstmt.setString(6, "Surround Audio, Stage Lighting, Dual Laser Projectors, Central AC");
            pstmt.setBoolean(7, true);
            pstmt.setString(8, "Available");
            pstmt.addBatch();

            // 4. Seminar Hall
            pstmt.setString(1, "Seminar Hall B");
            pstmt.setString(2, "Hall");
            pstmt.setString(3, "Academic Block B");
            pstmt.setInt(4, 3);
            pstmt.setInt(5, 100);
            pstmt.setString(6, "Podium Mic, Projector, High-speed Wi-Fi, AC");
            pstmt.setBoolean(7, false);
            pstmt.setString(8, "Available");
            pstmt.addBatch();

            pstmt.executeBatch();
            logger.info("Seeded 4 sample facilities (Classroom, Laboratory, Halls).");
        }
    }

    private static void seedEquipment(Connection conn) throws SQLException {
        String checkSql = "SELECT COUNT(*) FROM equipment";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(checkSql)) {
            if (rs.next() && rs.getInt(1) > 0) {
                logger.info("Equipment table already contains records. Skipping equipment seeding.");
                return;
            }
        }

        String sql = "INSERT INTO equipment (facility_id, name, category, serial_no, purchase_date, condition_status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            // Equipment in Lecture Hall 101 (facility_id = 1)
            pstmt.setInt(1, 1);
            pstmt.setString(2, "Epson Short-Throw Projector");
            pstmt.setString(3, "Audio-Visual");
            pstmt.setString(4, "PRJ-EPS-101");
            pstmt.setDate(5, Date.valueOf("2024-01-15"));
            pstmt.setString(6, "Good");
            pstmt.addBatch();

            // Equipment in Advanced Computing Lab (facility_id = 2)
            pstmt.setInt(1, 2);
            pstmt.setString(2, "Dell Precision 3660 Workstations (Rack 1)");
            pstmt.setString(3, "Computing");
            pstmt.setString(4, "PC-DLL-2024-01");
            pstmt.setDate(5, Date.valueOf("2024-02-10"));
            pstmt.setString(6, "Good");
            pstmt.addBatch();

            // Equipment in Main Auditorium (facility_id = 3)
            pstmt.setInt(1, 3);
            pstmt.setString(2, "Yamaha Digital Sound Mixer 32-Ch");
            pstmt.setString(3, "Audio");
            pstmt.setString(4, "AUD-YMH-3201");
            pstmt.setDate(5, Date.valueOf("2023-09-01"));
            pstmt.setString(6, "Needs Repair");
            pstmt.addBatch();

            pstmt.executeBatch();
            logger.info("Seeded sample equipment linked to facilities.");
        }
    }
}
