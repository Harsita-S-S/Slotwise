package com.fms.model;

import java.util.Map;

/**
 * Encapsulates a facility and the statuses of its 10 daily time slots for the visual matrix (FR-BKG-02).
 */
public class FacilitySlotSummary {
    private final Facility facility;
    private final Map<Integer, SlotStatus> slotStatuses;
    private final Map<Integer, Booking> slotBookings;

    public FacilitySlotSummary(Facility facility, Map<Integer, SlotStatus> slotStatuses, Map<Integer, Booking> slotBookings) {
        this.facility = facility;
        this.slotStatuses = slotStatuses;
        this.slotBookings = slotBookings;
    }

    public Facility getFacility() {
        return facility;
    }

    public Map<Integer, SlotStatus> getSlotStatuses() {
        return slotStatuses;
    }

    public Map<Integer, Booking> getSlotBookings() {
        return slotBookings;
    }

    public SlotStatus getStatusForSlot(int slotNo) {
        return slotStatuses.getOrDefault(slotNo, SlotStatus.FREE);
    }
}
