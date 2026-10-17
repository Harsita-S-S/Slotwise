package com.fms.model;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Data Transfer Object representing a past or active maintenance event for an equipment item.
 * Used to display equipment maintenance history per FR-EQP-04.
 */
public class MaintenanceHistoryItem {

    private int requestId;
    private int equipmentId;
    private String equipmentName;
    private String description;
    private Priority priority;
    private MaintenanceStatus status;
    private Date scheduledDate;
    private Timestamp createdAt;
    private Timestamp completedAt;
    private String staffName;
    private String note;

    public MaintenanceHistoryItem() {
    }

    public MaintenanceHistoryItem(int requestId, int equipmentId, String equipmentName,
                                  String description, Priority priority, MaintenanceStatus status,
                                  Date scheduledDate, Timestamp createdAt, Timestamp completedAt,
                                  String staffName, String note) {
        this.requestId = requestId;
        this.equipmentId = equipmentId;
        this.equipmentName = equipmentName;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.scheduledDate = scheduledDate;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.staffName = staffName;
        this.note = note;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
    }

    public int getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(int equipmentId) {
        this.equipmentId = equipmentId;
    }

    public String getEquipmentName() {
        return equipmentName;
    }

    public void setEquipmentName(String equipmentName) {
        this.equipmentName = equipmentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public MaintenanceStatus getStatus() {
        return status;
    }

    public void setStatus(MaintenanceStatus status) {
        this.status = status;
    }

    public Date getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(Date scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Timestamp completedAt) {
        this.completedAt = completedAt;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
