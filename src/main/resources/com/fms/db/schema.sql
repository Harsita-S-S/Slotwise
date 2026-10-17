-- ==========================================================
-- Facility Management System (FMS)
-- Database Schema Definition
-- Target Database: MySQL 8.x
-- Dependency Order:
-- users -> staff -> facilities -> equipment -> time_slots
--   -> bookings -> maintenance_requests -> maintenance_log
--   -> notifications -> audit_log
-- ==========================================================

CREATE DATABASE IF NOT EXISTS fms_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE fms_db;

-- 1. Users Table (Authentication & Role Management - Module 1, Sec 4.1, BR-11)
CREATE TABLE IF NOT EXISTS users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    salt VARCHAR(50) DEFAULT NULL,
    role ENUM('Admin', 'Staff', 'User') NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Staff Table (Staff Management - Module 4, Sec 4.4, BR-10)
CREATE TABLE IF NOT EXISTS staff (
    staff_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL UNIQUE,
    specialization VARCHAR(50) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_staff_user FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3. Facilities Table (Facility Management - Module 2, Sec 4.2, BR-08)
CREATE TABLE IF NOT EXISTS facilities (
    facility_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    type ENUM('Classroom', 'Laboratory', 'Hall') NOT NULL,
    building VARCHAR(100) NOT NULL,
    floor INT NOT NULL,
    capacity INT NOT NULL,
    amenities TEXT,
    requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
    status ENUM('Available', 'Under Maintenance', 'Inactive') NOT NULL DEFAULT 'Available',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_facility_building_name UNIQUE (building, name)
) ENGINE=InnoDB;

-- 4. Equipment Table (Equipment Management - Module 3, Sec 4.3)
CREATE TABLE IF NOT EXISTS equipment (
    equipment_id INT AUTO_INCREMENT PRIMARY KEY,
    facility_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    category VARCHAR(50) NOT NULL,
    serial_no VARCHAR(100) NOT NULL UNIQUE,
    purchase_date DATE NOT NULL,
    condition_status ENUM('Good', 'Needs Repair', 'Under Repair', 'Retired') NOT NULL DEFAULT 'Good',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_equipment_facility FOREIGN KEY (facility_id) 
        REFERENCES facilities(facility_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 5. Time Slots Table (Fixed Slots 8 AM - 6 PM - Sec 1.5, 2.3)
CREATE TABLE IF NOT EXISTS time_slots (
    slot_no INT PRIMARY KEY,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL
) ENGINE=InnoDB;

-- 6. Bookings Table (Availability & Booking - Module 5, Sec 4.5, BR-01 to BR-05, Sec 7.3)
CREATE TABLE IF NOT EXISTS bookings (
    booking_id INT AUTO_INCREMENT PRIMARY KEY,
    facility_id INT NOT NULL,
    user_id INT NOT NULL,
    booking_date DATE NOT NULL,
    slot_no INT NOT NULL,
    purpose VARCHAR(255) NOT NULL,
    attendees INT NOT NULL,
    status ENUM('Pending', 'Approved', 'Rejected', 'Cancelled', 'Completed') NOT NULL DEFAULT 'Approved',
    rejection_reason VARCHAR(255) NULL,
    active_flag TINYINT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_booking_facility FOREIGN KEY (facility_id) 
        REFERENCES facilities(facility_id),
    CONSTRAINT fk_booking_user FOREIGN KEY (user_id) 
        REFERENCES users(user_id),
    CONSTRAINT fk_booking_slot FOREIGN KEY (slot_no) 
        REFERENCES time_slots(slot_no),
    -- Composite unique key: active_flag is 1 for live bookings and NULL for cancelled/rejected,
    -- allowing MySQL unique constraint to enforce BR-01 while permitting rebooking of freed slots.
    CONSTRAINT uk_facility_date_slot_active UNIQUE (facility_id, booking_date, slot_no, active_flag),
    INDEX idx_bookings_date_facility (booking_date, facility_id)
) ENGINE=InnoDB;

-- 7. Maintenance Requests Table (Maintenance Management - Module 6, Sec 4.6, Standout FR-X1)
CREATE TABLE IF NOT EXISTS maintenance_requests (
    request_id INT AUTO_INCREMENT PRIMARY KEY,
    facility_id INT NOT NULL,
    equipment_id INT NULL,
    raised_by INT NOT NULL,
    assigned_staff INT NULL,
    description TEXT NOT NULL,
    priority ENUM('Low', 'Medium', 'High', 'Urgent') NOT NULL DEFAULT 'Medium',
    status ENUM('Reported', 'Assigned', 'In Progress', 'Completed') NOT NULL DEFAULT 'Reported',
    scheduled_date DATE NULL,
    start_slot INT NULL,
    end_slot INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_mr_facility FOREIGN KEY (facility_id) 
        REFERENCES facilities(facility_id),
    CONSTRAINT fk_mr_equipment FOREIGN KEY (equipment_id) 
        REFERENCES equipment(equipment_id) ON DELETE SET NULL,
    CONSTRAINT fk_mr_raised_by FOREIGN KEY (raised_by) 
        REFERENCES users(user_id),
    CONSTRAINT fk_mr_staff FOREIGN KEY (assigned_staff) 
        REFERENCES staff(staff_id) ON DELETE SET NULL,
    CONSTRAINT fk_mr_start_slot FOREIGN KEY (start_slot) 
        REFERENCES time_slots(slot_no),
    CONSTRAINT fk_mr_end_slot FOREIGN KEY (end_slot) 
        REFERENCES time_slots(slot_no),
    INDEX idx_mr_status_date (status, scheduled_date)
) ENGINE=InnoDB;

-- 8. Maintenance Log Table (Maintenance Status Audit Trail - Module 6, Sec 4.6, BR-09)
CREATE TABLE IF NOT EXISTS maintenance_log (
    log_id INT AUTO_INCREMENT PRIMARY KEY,
    request_id INT NOT NULL,
    old_status VARCHAR(50) NOT NULL,
    new_status VARCHAR(50) NOT NULL,
    changed_by INT NOT NULL,
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    note TEXT,
    CONSTRAINT fk_mlog_request FOREIGN KEY (request_id) 
        REFERENCES maintenance_requests(request_id) ON DELETE CASCADE,
    CONSTRAINT fk_mlog_user FOREIGN KEY (changed_by) 
        REFERENCES users(user_id)
) ENGINE=InnoDB;

-- 9. Notifications Table (Notifications System - Module 7, Sec 4.7)
CREATE TABLE IF NOT EXISTS notifications (
    notif_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,
    ref_key VARCHAR(100) NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE CASCADE,
    INDEX idx_notif_user_read (user_id, is_read)
) ENGINE=InnoDB;

-- 10. Audit Log Table (Stretch Goal - Section 5.1)
CREATE TABLE IF NOT EXISTS audit_log (
    audit_id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    action VARCHAR(100) NOT NULL,
    entity VARCHAR(50) NOT NULL,
    entity_id INT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) 
        REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;
