package com.fms.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a facility in the system.
 * Covers requirements FR-FAC-01 through FR-FAC-06.
 */
public class Facility {

    private int facilityId;
    private String name;
    private FacilityType type;
    private String building;
    private int floor;
    private int capacity;
    private String amenities;
    private boolean requiresApproval;
    private FacilityStatus status;
    private Timestamp createdAt;

    /** Default constructor */
    public Facility() {
        this.status = FacilityStatus.Available;
        this.requiresApproval = false;
    }

    /** Constructor for creating a new facility */
    public Facility(String name, FacilityType type, String building, int floor,
                    int capacity, String amenities, boolean requiresApproval) {
        this.name = name;
        this.type = type;
        this.building = building;
        this.floor = floor;
        this.capacity = capacity;
        this.amenities = amenities;
        this.requiresApproval = requiresApproval;
        this.status = FacilityStatus.Available;
    }

    /** Full constructor */
    public Facility(int facilityId, String name, FacilityType type, String building,
                    int floor, int capacity, String amenities, boolean requiresApproval,
                    FacilityStatus status, Timestamp createdAt) {
        this.facilityId = facilityId;
        this.name = name;
        this.type = type;
        this.building = building;
        this.floor = floor;
        this.capacity = capacity;
        this.amenities = amenities;
        this.requiresApproval = requiresApproval;
        this.status = status;
        this.createdAt = createdAt;
    }

    // Getters and Setters

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

    public FacilityType getType() {
        return type;
    }

    public void setType(FacilityType type) {
        this.type = type;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public String getAmenities() {
        return amenities;
    }

    public void setAmenities(String amenities) {
        this.amenities = amenities;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }

    public FacilityStatus getStatus() {
        return status;
    }

    public void setStatus(FacilityStatus status) {
        this.status = status;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Splits the comma-separated amenities string into a clean list of amenity names.
     *
     * @return List of amenity strings
     */
    public List<String> getAmenityList() {
        List<String> list = new ArrayList<>();
        if (amenities == null || amenities.trim().isEmpty()) {
            return list;
        }

        String[] parts = amenities.split(",");
        for (int i = 0; i < parts.length; i++) {
            String item = parts[i].trim();
            if (!item.isEmpty()) {
                list.add(item);
            }
        }
        return list;
    }

    /**
     * Checks if this facility provides a specific amenity (case-insensitive).
     *
     * @param amenityName The amenity to check for
     * @return true if the facility has the amenity
     */
    public boolean hasAmenity(String amenityName) {
        if (amenities == null || amenityName == null) {
            return false;
        }

        String search = amenityName.trim().toLowerCase();
        List<String> list = getAmenityList();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).toLowerCase().contains(search)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return name + " (" + building + ", Floor " + floor + ")";
    }
}
