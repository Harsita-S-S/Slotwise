-- ==========================================================
-- Facility Management System (FMS) - Seed Data Script
-- Target Database: MySQL 8.x (fms_db)
-- Seeds:
-- 1. 10 fixed time slots (08:00 to 18:00)
-- 2. Core facilities across different buildings, types, and approval settings
-- 3. Sample equipment with realistic serial numbers and conditions
-- 4. Initial test accounts for Admin, Staff, and regular Users
-- ==========================================================

USE fms_db;

-- 1. Insert 10 Fixed Time Slots (Sec 1.5, Sec 2.3)
INSERT INTO time_slots (slot_no, start_time, end_time) VALUES
(1, '08:00:00', '09:00:00'),
(2, '09:00:00', '10:00:00'),
(3, '10:00:00', '11:00:00'),
(4, '11:00:00', '12:00:00'),
(5, '12:00:00', '13:00:00'),
(6, '13:00:00', '14:00:00'),
(7, '14:00:00', '15:00:00'),
(8, '15:00:00', '16:00:00'),
(9, '16:00:00', '17:00:00'),
(10, '17:00:00', '18:00:00')
ON DUPLICATE KEY UPDATE start_time=VALUES(start_time), end_time=VALUES(end_time);

-- 2. Insert Sample Facilities (Module 2, Sec 4.2)
INSERT INTO facilities (facility_id, name, type, building, floor, capacity, amenities, requires_approval, status) VALUES
(1, 'Lecture Hall 101', 'Classroom', 'Academic Block A', 1, 50, 'Epson Projector, Whiteboard, Wi-Fi, AC', FALSE, 'Available'),
(2, 'Advanced Computing Lab', 'Laboratory', 'Tech Block', 2, 60, '60 Dell Workstations, Gigabit LAN, AC, Smart Board', TRUE, 'Available'),
(3, 'Main Auditorium', 'Hall', 'Central Block', 1, 300, 'Surround Audio, Stage Lighting, Dual Laser Projectors, Central AC', TRUE, 'Available'),
(4, 'Seminar Hall B', 'Hall', 'Academic Block B', 3, 100, 'Podium Mic, Projector, High-speed Wi-Fi, AC', FALSE, 'Available')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 3. Insert Sample Equipment (Module 3, Sec 4.3)
INSERT INTO equipment (equipment_id, facility_id, name, category, serial_no, purchase_date, condition_status) VALUES
(1, 1, 'Epson Short-Throw Projector', 'Audio-Visual', 'PRJ-EPS-101', '2024-01-15', 'Good'),
(2, 2, 'Dell Precision 3660 Workstations (Rack 1)', 'Computing', 'PC-DLL-2024-01', '2024-02-10', 'Good'),
(3, 3, 'Yamaha Digital Sound Mixer 32-Ch', 'Audio', 'AUD-YMH-3201', '2023-09-01', 'Needs Repair')
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- Note: User accounts and Staff profiles are managed with real salted BCrypt hashes
-- generated directly by DatabaseInitializer / PasswordUtil upon startup:
-- - admin: Admin@123 (Role: Admin)
-- - tech_raj: Staff@123 (Role: Staff, Electrician)
-- - tech_priya: Staff@123 (Role: Staff, Lab Technician)
-- - harsita: User@123 (Role: User)
-- - sunanda: User@123 (Role: User)
