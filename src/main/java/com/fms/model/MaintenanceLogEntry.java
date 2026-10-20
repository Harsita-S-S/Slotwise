package com.fms.model;

import java.sql.Timestamp;

/** One row of the maintenance status history: who changed what, when (FR-MNT-03, FR-MNT-05). */
public class MaintenanceLogEntry {
    private int logId;
    private int requestId;
    private String oldStatus;
    private String newStatus;
    private int changedBy;
    private String changedByName;
    private Timestamp changedAt;
    private String note;

    public MaintenanceLogEntry() {
    }

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public int getRequestId() { return requestId; }
    public void setRequestId(int requestId) { this.requestId = requestId; }

    public String getOldStatus() { return oldStatus; }
    public void setOldStatus(String oldStatus) { this.oldStatus = oldStatus; }

    public String getNewStatus() { return newStatus; }
    public void setNewStatus(String newStatus) { this.newStatus = newStatus; }

    public int getChangedBy() { return changedBy; }
    public void setChangedBy(int changedBy) { this.changedBy = changedBy; }

    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }

    public Timestamp getChangedAt() { return changedAt; }
    public void setChangedAt(Timestamp changedAt) { this.changedAt = changedAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}