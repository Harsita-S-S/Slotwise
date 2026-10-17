package com.fms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Represents a facility reservation in the Facility Management System.
 * Adheres to SRS Section 4.5 (FR-BKG-01 to FR-BKG-11) and Section 7.3.
 */
public class Booking {
    private int bookingId;
    private int facilityId;
    private String facilityName;
    private String facilityBuilding;
    private FacilityType facilityType;
    private int facilityCapacity;

    private int userId;
    private String username;
    private String userFullName;

    private LocalDate bookingDate;
    private int slotNo;
    private String slotTime; // e.g. "08:00 - 09:00"
    private String purpose;
    private int attendees;
    private BookingStatus status;
    private String rejectionReason;
    private Integer activeFlag; // 1 for active (Pending, Approved), null for Cancelled/Rejected/Completed
    private LocalDateTime createdAt;

    public Booking() {
    }

    public Booking(int facilityId, int userId, LocalDate bookingDate, int slotNo,
                   String purpose, int attendees, BookingStatus status) {
        this.facilityId = facilityId;
        this.userId = userId;
        this.bookingDate = bookingDate;
        this.slotNo = slotNo;
        this.purpose = purpose;
        this.attendees = attendees;
        this.status = status;
        this.activeFlag = (status == BookingStatus.Approved || status == BookingStatus.Pending) ? 1 : null;
    }

    public static String formatSlotTime(int slotNo) {
        if (slotNo < 1 || slotNo > 10) {
            return "Slot " + slotNo;
        }
        int startHour = 7 + slotNo;
        int endHour = startHour + 1;
        return String.format("%02d:00 - %02d:00", startHour, endHour);
    }

    // Getters and Setters

    public int getBookingId() {
        return bookingId;
    }

    public void setBookingId(int bookingId) {
        this.bookingId = bookingId;
    }

    public int getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(int facilityId) {
        this.facilityId = facilityId;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getFacilityBuilding() {
        return facilityBuilding;
    }

    public void setFacilityBuilding(String facilityBuilding) {
        this.facilityBuilding = facilityBuilding;
    }

    public FacilityType getFacilityType() {
        return facilityType;
    }

    public void setFacilityType(FacilityType facilityType) {
        this.facilityType = facilityType;
    }

    public int getFacilityCapacity() {
        return facilityCapacity;
    }

    public void setFacilityCapacity(int facilityCapacity) {
        this.facilityCapacity = facilityCapacity;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUserFullName() {
        return userFullName;
    }

    public void setUserFullName(String userFullName) {
        this.userFullName = userFullName;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public int getSlotNo() {
        return slotNo;
    }

    public void setSlotNo(int slotNo) {
        this.slotNo = slotNo;
        this.slotTime = formatSlotTime(slotNo);
    }

    public String getSlotTime() {
        if (slotTime == null && slotNo > 0) {
            return formatSlotTime(slotNo);
        }
        return slotTime;
    }

    public void setSlotTime(String slotTime) {
        this.slotTime = slotTime;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public int getAttendees() {
        return attendees;
    }

    public void setAttendees(int attendees) {
        this.attendees = attendees;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
        this.activeFlag = (status == BookingStatus.Approved || status == BookingStatus.Pending) ? 1 : null;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public Integer getActiveFlag() {
        return activeFlag;
    }

    public void setActiveFlag(Integer activeFlag) {
        this.activeFlag = activeFlag;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
