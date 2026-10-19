package com.fms;

import com.fms.model.MaintenanceStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MaintenanceStatusTest {

    @Test
    @DisplayName("DB value 'In Progress' maps to InProgress and back")
    public void testInProgressMapping() {
        assertEquals(MaintenanceStatus.InProgress, MaintenanceStatus.fromDbValue("In Progress"));
        assertEquals("In Progress", MaintenanceStatus.InProgress.getDbValue());
    }

    @Test
    @DisplayName("Every status round-trips through its DB value")
    public void testRoundTrip() {
        for (MaintenanceStatus s : MaintenanceStatus.values()) {
            assertEquals(s, MaintenanceStatus.fromDbValue(s.getDbValue()));
        }
    }
}