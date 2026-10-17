package com.fms.model;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * DTO representing an open or active maintenance task assigned to a staff member.
 * Used for FR-STF-03 workload and task tracking.
 */
public class StaffTaskSummary {

    private int requestId;
    private int facilityId;
    private String facilityName;
    private Integer equipmentId;
    private String equipmentName;
    private String description;
    private Priority priority;
    private MaintenanceStatus status;
    private Date scheduledDate;
    private Timestamp createdAt;

    public StaffTaskSummary() {
    }

    public StaffTaskSummary(int requestId, int facilityId, String facilityName,
                            Integer equipmentId, String equipmentName, String description,
                            Priority priority, MaintenanceStatus status, Date scheduledDate,
                            Timestamp createdAt) {
        this.requestId = requestId;
        this.facilityId = facilityId;
        this.facilityName = facilityName;
        this.equipmentId = equipmentId;
        this.equipmentName = equipmentName;
        this.description = description;
        this.priority = priority;
        this.status = status;
        this.scheduledDate = scheduledDate;
        this.createdAt = createdAt;
    }

    public int getRequestId() {
        return requestId;
    }

    public void setRequestId(int requestId) {
        this.requestId = requestId;
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

    public Integer getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(Integer equipmentId) {
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
}
