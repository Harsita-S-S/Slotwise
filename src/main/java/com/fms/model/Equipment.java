package com.fms.model;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * Model representing an institutional equipment item.
 * Covers requirements FR-EQP-01 through FR-EQP-04.
 */
public class Equipment {

    private int equipmentId;
    private int facilityId;
    private String name;
    private String category;
    private String serialNo;
    private Date purchaseDate;
    private EquipmentCondition conditionStatus;
    private Timestamp createdAt;

    // Display helper (populated via join with facilities table)
    private String facilityName;

    /** Default constructor */
    public Equipment() {
        this.conditionStatus = EquipmentCondition.Good;
    }

    /** Constructor for creating new equipment */
    public Equipment(int facilityId, String name, String category,
                     String serialNo, Date purchaseDate) {
        this.facilityId = facilityId;
        this.name = name;
        this.category = category;
        this.serialNo = serialNo;
        this.purchaseDate = purchaseDate;
        this.conditionStatus = EquipmentCondition.Good;
    }

    /** Full constructor */
    public Equipment(int equipmentId, int facilityId, String name, String category,
                     String serialNo, Date purchaseDate, EquipmentCondition conditionStatus,
                     Timestamp createdAt) {
        this.equipmentId = equipmentId;
        this.facilityId = facilityId;
        this.name = name;
        this.category = category;
        this.serialNo = serialNo;
        this.purchaseDate = purchaseDate;
        this.conditionStatus = conditionStatus;
        this.createdAt = createdAt;
    }

    // Getters and Setters

    public int getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(int equipmentId) {
        this.equipmentId = equipmentId;
    }

    public int getFacilityId() {
        return facilityId;
    }

    public void setFacilityId(int facilityId) {
        this.facilityId = facilityId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public void setSerialNo(String serialNo) {
        this.serialNo = serialNo;
    }

    public Date getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(Date purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public EquipmentCondition getConditionStatus() {
        return conditionStatus;
    }

    public void setConditionStatus(EquipmentCondition conditionStatus) {
        this.conditionStatus = conditionStatus;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public String getFacilityName() {
        return facilityName;
    }

    public void setFacilityName(String facilityName) {
        this.facilityName = facilityName;
    }

    @Override
    public String toString() {
        return name + " [" + serialNo + "] (" + category + ")";
    }
}
