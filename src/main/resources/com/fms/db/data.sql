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
INSERT INTO facilities (name, type, building, floor, capacity, amenities, requires_approval, status) VALUES
-- Auditoriums & Conference (A, D, F Blocks)
('A-101 Main Auditorium', 'Hall', 'A-Block', 1, 600, 'Stage, Central AC, Pro Audio, Projector', 1, 'Available'),
('A-201 Executive Conference Room', 'Hall', 'A-Block', 2, 40, 'Conference Table, Smart Display, AC', 1, 'Available'),
('D-101 Seminar Hall D', 'Hall', 'D-Block', 1, 200, 'Tiered Seating, Audio System, AC', 1, 'Available'),
('F-101 Amphitheater & Audit', 'Hall', 'F-Block', 1, 500, 'Large Stage, Dolby Sound, Central AC', 1, 'Available'),
('F-201 Conference Suite F', 'Hall', 'F-Block', 2, 50, 'Presentation Screen, Smart Board, AC', 1, 'Available'),

-- Specialized Labs (J & M Blocks)
('J-201 Advanced Computing Lab', 'Laboratory', 'J-Block', 2, 60, '60 Workstations, LAN, AC, Projector', 1, 'Available'),
('J-301 AI & Machine Learning Lab', 'Laboratory', 'J-Block', 3, 60, 'High-End GPU Workstations, AC, LAN', 1, 'Available'),
('M-201 Embedded Systems Lab', 'Laboratory', 'M-Block', 2, 50, 'Oscilloscopes, Microcontrollers, AC', 1, 'Available'),
('M-301 VLSI & Hardware Lab', 'Laboratory', 'M-Block', 3, 45, 'Workstations, EDA Tools, AC, LAN', 1, 'Available'),
('K-201 CAD Design Studio', 'Laboratory', 'K-Block', 2, 40, 'Workstations, Plotters, AC, Projector', 1, 'Available'),

-- Classrooms & Lecture Halls across all blocks
('A-202 Lecture Classroom A', 'Classroom', 'A-Block', 2, 60, 'Projector, Whiteboard, Wi-Fi', 0, 'Available'),
('D-202 Classroom D-202', 'Classroom', 'D-Block', 2, 70, 'Projector, Whiteboard, Wi-Fi', 0, 'Available'),
('E-201 Smart Classroom E-201', 'Classroom', 'E-Block', 2, 75, 'Projector, Smart Board, Wi-Fi, AC', 0, 'Available'),
('G-201 Lecture Hall G-201', 'Classroom', 'G-Block', 2, 120, 'Tiered Seating, Projector, Audio, Wi-Fi', 0, 'Available'),
('H-201 Classroom H-201', 'Classroom', 'H-Block', 2, 60, 'Projector, Whiteboard, Wi-Fi', 0, 'Available'),
('I-201 Classroom I-201', 'Classroom', 'I-Block', 2, 60, 'Projector, Whiteboard, Wi-Fi', 0, 'Available'),
('N-201 Classroom N-201', 'Classroom', 'N-Block', 2, 70, 'Projector, Wi-Fi, Ergonomic Desks', 0, 'Available'),
('Q-201 Lecture Theater Q-201', 'Classroom', 'Q-Block', 2, 100, 'Tiered Seating, Dual Projectors, AC', 0, 'Available'),
('Y-201 Research Studio Y-201', 'Hall', 'Y-Block', 2, 35, 'Discussion Tables, Smart Display, AC', 1, 'Available')
ON DUPLICATE KEY UPDATE type=VALUES(type), floor=VALUES(floor), capacity=VALUES(capacity), amenities=VALUES(amenities), requires_approval=VALUES(requires_approval), status=VALUES(status);

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
