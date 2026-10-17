package com.fms.service;

import com.fms.dao.EquipmentDAO;
import com.fms.dao.FacilityDAO;
import com.fms.exception.InvalidInputException;
import com.fms.model.Equipment;
import com.fms.model.EquipmentCondition;
import com.fms.model.Facility;
import com.fms.model.MaintenanceHistoryItem;
import com.fms.model.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Equipment Management (Module 3).
 * Enforces business rules, role permissions, validations, and audit logs.
 *
 * Covers:
 * - FR-EQP-01: Admin/Staff adds equipment with facility linkage.
 * - FR-EQP-02: Tracks equipment condition (Good, Needs Repair, Under Repair, Retired).
 * - FR-EQP-03: Lists equipment of a facility and moves equipment between facilities.
 * - FR-EQP-04: Shows maintenance history for equipment items.
 */
public class EquipmentService {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentService.class);

    private final EquipmentDAO equipmentDAO;
    private final FacilityDAO facilityDAO;

    public EquipmentService() {
        this.equipmentDAO = new EquipmentDAO();
        this.facilityDAO = new FacilityDAO();
    }

    // ---------------------------------------------------------------
    // 1. Add Equipment (FR-EQP-01)
    // ---------------------------------------------------------------

    /**
     * Adds new equipment linked to a facility (Admin or Staff only).
     *
     * @param facilityId   Linked facility ID
     * @param name         Equipment name (e.g., "Dell Precision 3660")
     * @param category     Category (e.g., "Workstation", "Projector")
     * @param serialNo     Unique serial number
     * @param purchaseDate Date of purchase (cannot be in the future)
     * @return The saved Equipment entity with generated ID
     */
    public Equipment addEquipment(int facilityId, String name, String category,
                                 String serialNo, Date purchaseDate) {
        // Enforce Admin or Staff permission (FR-EQP-01)
        SessionManager.getInstance().requireRole(Role.Staff);

        // Step 1: Input validation
        validateEquipmentInput(facilityId, name, category, serialNo, purchaseDate);

        // Step 2: Prevent duplicate serial number
        if (equipmentDAO.isSerialNumberTaken(serialNo, 0)) {
            throw new InvalidInputException("serialNo",
                    "Equipment with serial number '" + serialNo.trim() + "' is already registered.");
        }

        // Step 3: Verify linked facility exists
        Optional<Facility> facilityOpt = facilityDAO.findById(facilityId);
        if (facilityOpt.isEmpty()) {
            throw new InvalidInputException("facilityId", "Linked facility ID " + facilityId + " does not exist.");
        }

        // Step 4: Create and persist
        Equipment eq = new Equipment(facilityId, name.trim(), category.trim(), serialNo.trim(), purchaseDate);
        Equipment saved = equipmentDAO.save(eq);
        saved.setFacilityName(facilityOpt.get().getName());

        logger.info("Equipment added: '{}' [{}] linked to facility '{}' by user '{}'",
                saved.getName(), saved.getSerialNo(), saved.getFacilityName(),
                SessionManager.getInstance().getCurrentUserDisplayName());

        return saved;
    }

    // ---------------------------------------------------------------
    // 2. Condition Tracking (FR-EQP-02)
    // ---------------------------------------------------------------

    /**
     * Updates the operational condition of an equipment item (Admin or Staff only).
     *
     * @param equipmentId  The equipment ID
     * @param newCondition The new condition status (Good, Needs Repair, Under Repair, Retired)
     * @return true if updated
     */
    public boolean updateCondition(int equipmentId, EquipmentCondition newCondition) {
        SessionManager.getInstance().requireRole(Role.Staff);

        if (newCondition == null) {
            throw new InvalidInputException("conditionStatus", "Condition cannot be null.");
        }

        Optional<Equipment> opt = equipmentDAO.findById(equipmentId);
        if (opt.isEmpty()) {
            throw new InvalidInputException("equipmentId", "Equipment ID " + equipmentId + " not found.");
        }

        boolean updated = equipmentDAO.updateCondition(equipmentId, newCondition);
        if (updated) {
            logger.info("Condition for equipment ID {} updated to {} by user '{}'",
                    equipmentId, newCondition, SessionManager.getInstance().getCurrentUserDisplayName());
        }
        return updated;
    }

    // ---------------------------------------------------------------
    // 3. Facility Equipment & Movement (FR-EQP-03)
    // ---------------------------------------------------------------

    /**
     * Lists all equipment belonging to a specific facility.
     * Accessible by all user roles (FR-EQP-03).
     *
     * @param facilityId The facility ID
     * @return List of equipment items in that facility
     */
    public List<Equipment> getEquipmentByFacility(int facilityId) {
        return equipmentDAO.findByFacilityId(facilityId);
    }

    /**
     * Moves an equipment item to another facility (Admin or Staff only - FR-EQP-03).
     *
     * @param equipmentId   The equipment ID to move
     * @param newFacilityId The destination facility ID
     * @return true if move succeeded
     */
    public boolean moveEquipment(int equipmentId, int newFacilityId) {
        SessionManager.getInstance().requireRole(Role.Staff);

        Optional<Equipment> eqOpt = equipmentDAO.findById(equipmentId);
        if (eqOpt.isEmpty()) {
            throw new InvalidInputException("equipmentId", "Equipment ID " + equipmentId + " does not exist.");
        }

        Equipment eq = eqOpt.get();
        if (eq.getFacilityId() == newFacilityId) {
            throw new InvalidInputException("newFacilityId", "Equipment is already located in this facility.");
        }

        // Verify destination facility exists
        Optional<Facility> destFacilityOpt = facilityDAO.findById(newFacilityId);
        if (destFacilityOpt.isEmpty()) {
            throw new InvalidInputException("newFacilityId", "Destination facility ID " + newFacilityId + " does not exist.");
        }

        boolean moved = equipmentDAO.moveEquipment(equipmentId, newFacilityId);
        if (moved) {
            logger.info("Equipment '{}' [{}] moved from facility ID {} to facility '{}' (ID {}) by user '{}'",
                    eq.getName(), eq.getSerialNo(), eq.getFacilityId(),
                    destFacilityOpt.get().getName(), newFacilityId,
                    SessionManager.getInstance().getCurrentUserDisplayName());
        }
        return moved;
    }

    // ---------------------------------------------------------------
    // 4. Maintenance History (FR-EQP-04)
    // ---------------------------------------------------------------

    /**
     * Retrieves the maintenance history records for an equipment item.
     * Accessible by all user roles.
     *
     * @param equipmentId The equipment ID
     * @return List of maintenance history entries
     */
    public List<MaintenanceHistoryItem> getMaintenanceHistory(int equipmentId) {
        return equipmentDAO.getMaintenanceHistory(equipmentId);
    }

    // ---------------------------------------------------------------
    // Search, Queries, & Admin Updates
    // ---------------------------------------------------------------

    /**
     * Searches and filters equipment items.
     *
     * @param keyword    Search term for name, serial, or category
     * @param facilityId Filter by facility (optional)
     * @param category   Filter by category (optional)
     * @param condition  Filter by condition (optional)
     * @return Filtered list of equipment
     */
    public List<Equipment> searchEquipment(String keyword, Integer facilityId,
                                           String category, EquipmentCondition condition) {
        return equipmentDAO.searchAndFilter(keyword, facilityId, category, condition);
    }

    public List<Equipment> getAllEquipment() {
        return equipmentDAO.findAll();
    }

    public Optional<Equipment> getEquipmentById(int id) {
        return equipmentDAO.findById(id);
    }

    public List<String> getAllCategories() {
        return equipmentDAO.getDistinctCategories();
    }

    public boolean updateEquipment(Equipment eq) {
        SessionManager.getInstance().requireRole(Role.Staff);

        if (eq == null || eq.getEquipmentId() <= 0) {
            throw new InvalidInputException("equipmentId", "Invalid equipment for update.");
        }

        validateEquipmentInput(eq.getFacilityId(), eq.getName(), eq.getCategory(),
                eq.getSerialNo(), eq.getPurchaseDate());

        if (equipmentDAO.isSerialNumberTaken(eq.getSerialNo(), eq.getEquipmentId())) {
            throw new InvalidInputException("serialNo",
                    "Equipment with serial number '" + eq.getSerialNo() + "' is already registered.");
        }

        return equipmentDAO.update(eq);
    }

    public boolean deleteEquipment(int id) {
        SessionManager.getInstance().requireRole(Role.Admin);
        return equipmentDAO.delete(id);
    }

    // ---------------------------------------------------------------
    // Validation Helper
    // ---------------------------------------------------------------

    private void validateEquipmentInput(int facilityId, String name, String category,
                                        String serialNo, Date purchaseDate) {
        if (facilityId <= 0) {
            throw new InvalidInputException("facilityId", "Please select a valid linked facility.");
        }

        if (name == null || name.trim().isEmpty()) {
            throw new InvalidInputException("name", "Equipment name cannot be empty.");
        }

        if (category == null || category.trim().isEmpty()) {
            throw new InvalidInputException("category", "Equipment category cannot be empty.");
        }

        if (serialNo == null || serialNo.trim().isEmpty()) {
            throw new InvalidInputException("serialNo", "Serial number cannot be empty.");
        }

        if (purchaseDate == null) {
            throw new InvalidInputException("purchaseDate", "Purchase date cannot be null.");
        }

        // Purchase date cannot be in the future
        LocalDate today = LocalDate.now();
        if (purchaseDate.toLocalDate().isAfter(today)) {
            throw new InvalidInputException("purchaseDate", "Purchase date cannot be in the future.");
        }
    }
}
