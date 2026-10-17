package com.fms;

import com.fms.dao.FacilityDAO;
import com.fms.exception.AccessDeniedException;
import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import com.fms.service.EquipmentService;
import com.fms.service.SessionManager;
import com.fms.util.DBConnection;
import com.fms.util.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Equipment Management (Module 3).
 * Covers:
 * - FR-EQP-01: Admin/Staff adds equipment with name, category, serial number, purchase date, linked facility
 * - FR-EQP-02: Equipment condition tracking (Good, Needs Repair, Under Repair, Retired)
 * - FR-EQP-03: List equipment of a facility and move equipment to another facility
 * - FR-EQP-04: Show maintenance history of equipment item
 */
public class EquipmentServiceTest {

    private static EquipmentService equipmentService;
    private static FacilityDAO facilityDAO;
    private static User adminUser;
    private static User staffUser;
    private static User regularUser;
    private static Facility testFacility1;
    private static Facility testFacility2;

    @BeforeAll
    public static void setUpAll() {
        DatabaseInitializer.initializeDatabase();
        equipmentService = new EquipmentService();
        facilityDAO = new FacilityDAO();

        adminUser = new User();
        adminUser.setUserId(1);
        adminUser.setUsername("admin");
        adminUser.setRole(Role.Admin);
        adminUser.setFullName("Administrator");

        staffUser = new User();
        staffUser.setUserId(2);
        staffUser.setUsername("tech_raj");
        staffUser.setRole(Role.Staff);
        staffUser.setFullName("Rajesh Kumar");

        regularUser = new User();
        regularUser.setUserId(3);
        regularUser.setUsername("harsita");
        regularUser.setRole(Role.User);
        regularUser.setFullName("Harsita");

        // Fetch two facilities for testing equipment location and movement
        List<Facility> allFacilities = facilityDAO.findAll();
        assertTrue(allFacilities.size() >= 2, "Need at least 2 facilities for tests");
        testFacility1 = allFacilities.get(0);
        testFacility2 = allFacilities.get(1);
    }

    @BeforeEach
    public void setUp() {
        // Set admin session by default
        SessionManager.getInstance().setCurrentUser(adminUser);
    }

    @Test
    @DisplayName("FR-EQP-01: Admin/Staff successfully adds new equipment linked to a facility")
    public void testAddEquipmentSuccess() {
        // Test with Staff user
        SessionManager.getInstance().setCurrentUser(staffUser);

        String serial = "SN-TEST-" + System.currentTimeMillis();
        Date purchaseDate = Date.valueOf(LocalDate.now().minusDays(30));

        Equipment created = equipmentService.addEquipment(
                testFacility1.getFacilityId(),
                "Laser Projector 4K",
                "Projector",
                serial,
                purchaseDate
        );

        assertNotNull(created);
        assertTrue(created.getEquipmentId() > 0);
        assertEquals("Laser Projector 4K", created.getName());
        assertEquals("Projector", created.getCategory());
        assertEquals(serial, created.getSerialNo());
        assertEquals(EquipmentCondition.Good, created.getConditionStatus());
        assertEquals(testFacility1.getFacilityId(), created.getFacilityId());
    }

    @Test
    @DisplayName("FR-EQP-01: Regular user is blocked from adding equipment")
    public void testRegularUserCannotAddEquipment() {
        SessionManager.getInstance().setCurrentUser(regularUser);

        String serial = "SN-UNAUTH-" + System.currentTimeMillis();
        Date purchaseDate = Date.valueOf(LocalDate.now());

        assertThrows(AccessDeniedException.class, () ->
                equipmentService.addEquipment(testFacility1.getFacilityId(), "Unauthorized Monitor",
                        "Display", serial, purchaseDate)
        );
    }

    @Test
    @DisplayName("FR-EQP-01: Duplicate serial numbers and invalid inputs are rejected")
    public void testAddEquipmentValidation() {
        String serial = "SN-DUP-" + System.currentTimeMillis();
        Date validDate = Date.valueOf(LocalDate.now().minusDays(10));

        // Create first item
        equipmentService.addEquipment(testFacility1.getFacilityId(), "Scanner A", "Scanner", serial, validDate);

        // Attempt duplicate serial number -> Must fail!
        InvalidInputException ex = assertThrows(InvalidInputException.class, () ->
                equipmentService.addEquipment(testFacility2.getFacilityId(), "Scanner B", "Scanner", serial, validDate)
        );
        assertTrue(ex.getMessage().contains("already registered"));

        // Purchase date in future -> Must fail!
        Date futureDate = Date.valueOf(LocalDate.now().plusDays(10));
        assertThrows(InvalidInputException.class, () ->
                equipmentService.addEquipment(testFacility1.getFacilityId(), "Scanner C", "Scanner",
                        "SN-FUT-" + System.currentTimeMillis(), futureDate)
        );

        // Empty name -> Must fail!
        assertThrows(InvalidInputException.class, () ->
                equipmentService.addEquipment(testFacility1.getFacilityId(), "", "Scanner",
                        "SN-EMP-" + System.currentTimeMillis(), validDate)
        );
    }

    @Test
    @DisplayName("FR-EQP-02: Equipment condition transitions through Good, Needs Repair, Under Repair, Retired")
    public void testEquipmentConditionTracking() {
        String serial = "SN-COND-" + System.currentTimeMillis();
        Equipment eq = equipmentService.addEquipment(testFacility1.getFacilityId(), "Audio Amplifier",
                "Audio", serial, Date.valueOf(LocalDate.now()));

        int eqId = eq.getEquipmentId();
        assertEquals(EquipmentCondition.Good, eq.getConditionStatus());

        // Update to Needs Repair
        equipmentService.updateCondition(eqId, EquipmentCondition.NeedsRepair);
        assertEquals(EquipmentCondition.NeedsRepair, equipmentService.getEquipmentById(eqId).get().getConditionStatus());

        // Update to Under Repair
        equipmentService.updateCondition(eqId, EquipmentCondition.UnderRepair);
        assertEquals(EquipmentCondition.UnderRepair, equipmentService.getEquipmentById(eqId).get().getConditionStatus());

        // Update to Retired
        equipmentService.updateCondition(eqId, EquipmentCondition.Retired);
        assertEquals(EquipmentCondition.Retired, equipmentService.getEquipmentById(eqId).get().getConditionStatus());
    }

    @Test
    @DisplayName("FR-EQP-03: List equipment of a facility and move equipment to another facility")
    public void testListByFacilityAndMoveEquipment() {
        String serial = "SN-MOVE-" + System.currentTimeMillis();
        Equipment eq = equipmentService.addEquipment(testFacility1.getFacilityId(), "Oscilloscope",
                "Lab Instruments", serial, Date.valueOf(LocalDate.now()));

        int eqId = eq.getEquipmentId();

        // 1. Verify item is listed under facility 1
        List<Equipment> fac1List = equipmentService.getEquipmentByFacility(testFacility1.getFacilityId());
        boolean foundInFac1 = false;
        for (int i = 0; i < fac1List.size(); i++) {
            if (fac1List.get(i).getEquipmentId() == eqId) {
                foundInFac1 = true;
                break;
            }
        }
        assertTrue(foundInFac1, "Equipment should be in facility 1 inventory");

        // 2. Move equipment to facility 2
        boolean moved = equipmentService.moveEquipment(eqId, testFacility2.getFacilityId());
        assertTrue(moved, "Moving equipment should succeed");

        // 3. Verify item is now in facility 2 inventory
        List<Equipment> fac2List = equipmentService.getEquipmentByFacility(testFacility2.getFacilityId());
        boolean foundInFac2 = false;
        for (int i = 0; i < fac2List.size(); i++) {
            if (fac2List.get(i).getEquipmentId() == eqId) {
                foundInFac2 = true;
                break;
            }
        }
        assertTrue(foundInFac2, "Equipment should now be in facility 2 inventory");

        // 4. Moving to the same facility should throw an error
        assertThrows(InvalidInputException.class, () ->
                equipmentService.moveEquipment(eqId, testFacility2.getFacilityId())
        );
    }

    @Test
    @DisplayName("FR-EQP-04: Shows maintenance history for each equipment item")
    public void testGetMaintenanceHistory() throws SQLException {
        String serial = "SN-HIST-" + System.currentTimeMillis();
        Equipment eq = equipmentService.addEquipment(testFacility1.getFacilityId(), "3D Printer",
                "Manufacturing", serial, Date.valueOf(LocalDate.now()));

        int eqId = eq.getEquipmentId();

        // Insert a maintenance request linked to this equipment
        String insertMrSql = "INSERT INTO maintenance_requests (facility_id, equipment_id, raised_by, description, priority, status) " +
                             "VALUES (?, ?, 1, 'Extruder nozzle clogged and temperature sensor erratic', 'High', 'Reported')";

        try (Connection conn = DBConnection.getInstance().getConnection();
             PreparedStatement pstmt = conn.prepareStatement(insertMrSql)) {
            pstmt.setInt(1, testFacility1.getFacilityId());
            pstmt.setInt(2, eqId);
            pstmt.executeUpdate();
        }

        // Retrieve maintenance history (FR-EQP-04)
        List<MaintenanceHistoryItem> history = equipmentService.getMaintenanceHistory(eqId);
        assertNotNull(history);
        assertEquals(1, history.size());

        MaintenanceHistoryItem item = history.get(0);
        assertEquals(eqId, item.getEquipmentId());
        assertTrue(item.getDescription().contains("Extruder nozzle"));
        assertEquals(Priority.High, item.getPriority());
        assertEquals(MaintenanceStatus.Reported, item.getStatus());
    }
}
