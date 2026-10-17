package com.fms.model;

/**
 * Model class representing a campus maintenance staff member.
 * Links to a user login account (Role.Staff).
 * Covers requirements FR-STF-01, FR-STF-02, and FR-STF-03.
 */
public class Staff {

    private int staffId;
    private int userId;
    private StaffSpecialization specialization;
    private String phone;
    private boolean active;

    // Joined user details (from users table)
    private String fullName;
    private String username;
    private String email;

    // Workload metrics (FR-STF-03)
    private int openTaskCount;
    private int inProgressTaskCount;
    private int completedTaskCount;

    public Staff() {
        this.specialization = StaffSpecialization.General;
        this.active = true;
    }

    public Staff(int userId, StaffSpecialization specialization, String phone) {
        this.userId = userId;
        this.specialization = specialization;
        this.phone = phone;
        this.active = true;
    }

    public Staff(int staffId, int userId, StaffSpecialization specialization, String phone, boolean active) {
        this.staffId = staffId;
        this.userId = userId;
        this.specialization = specialization;
        this.phone = phone;
        this.active = active;
    }

    // Getters and Setters

    public int getStaffId() {
        return staffId;
    }

    public void setStaffId(int staffId) {
        this.staffId = staffId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public StaffSpecialization getSpecialization() {
        return specialization;
    }

    public void setSpecialization(StaffSpecialization specialization) {
        this.specialization = specialization;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getOpenTaskCount() {
        return openTaskCount;
    }

    public void setOpenTaskCount(int openTaskCount) {
        this.openTaskCount = openTaskCount;
    }

    public int getInProgressTaskCount() {
        return inProgressTaskCount;
    }

    public void setInProgressTaskCount(int inProgressTaskCount) {
        this.inProgressTaskCount = inProgressTaskCount;
    }

    public int getCompletedTaskCount() {
        return completedTaskCount;
    }

    public void setCompletedTaskCount(int completedTaskCount) {
        this.completedTaskCount = completedTaskCount;
    }

    @Override
    public String toString() {
        return (fullName != null ? fullName : "Staff #" + staffId) + " (" + specialization.getDisplayName() + ")";
    }
}
