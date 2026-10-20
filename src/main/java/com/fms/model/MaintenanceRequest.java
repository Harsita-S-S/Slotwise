
package com.fms.model;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Represents a maintenance request for a facility or equipment item.
 * Supports FR-MNT-01 through FR-MNT-06.
 */
public class MaintenanceRequest {

    private int requestId;
    private int facilityId;
    private Integer equipmentId;
    private int raisedByUserId;
    private Integer assignedStaffId;
    private String description;
    private Priority priority = Priority.Medium;
    private MaintenanceStatus status = MaintenanceStatus.Reported;
    private Date scheduledDate;
    private Integer startSlot;
    private Integer endSlot;
    private Timestamp createdAt;
    private Timestamp completedAt;

    // Display fields populated by DAO joins
    private String facilityName;
    private String equipmentName;
    private String raisedByName;
    private String assignedStaffName;
    private String latestNote;

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public int getFacilityId() { return facilityId; }
    public void setFacilityId(int facilityId) { this.facilityId = facilityId; }

    public Integer getEquipmentId() { return equipmentId; }
    public void setEquipmentId(Integer equipmentId) { this.equipmentId = equipmentId; }

    public int getRaisedByUserId() { return raisedByUserId; }
    public void setRaisedByUserId(int raisedByUserId) {
        this.raisedByUserId = raisedByUserId;
    }

    public Integer getAssignedStaffId() { return assignedStaffId; }
    public void setAssignedStaffId(Integer assignedStaffId) {
        this.assignedStaffId = assignedStaffId;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public MaintenanceStatus getStatus() { return status; }
    public void setStatus(MaintenanceStatus status) { this.status = status; }

    public Date getScheduledDate() { return scheduledDate; }
    public void setScheduledDate(Date scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public Integer getStartSlot() { return startSlot; }
    public void setStartSlot(Integer startSlot) { this.startSlot = startSlot; }

    public Integer getEndSlot() { return endSlot; }
    public void setEndSlot(Integer endSlot) { this.endSlot = endSlot; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }

    public String getFacilityName() { return facilityName; }
    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    public String getEquipmentName() { return equipmentName; }
    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getRaisedByName() { return raisedByName; }
    public void setRaisedByName(String raisedByName) {
        this.raisedByName = raisedByName;
    }

    public String getAssignedStaffName() { return assignedStaffName; }
    public void setAssignedStaffName(String assignedStaffName) {
        this.assignedStaffName = assignedStaffName;
    }

    public String getLatestNote() { return latestNote; }
    public void setLatestNote(String latestNote) { this.latestNote = latestNote; }
}
