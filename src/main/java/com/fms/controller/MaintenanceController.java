
package com.fms.controller;

import com.fms.model.*;
import com.fms.service.EquipmentService;
import com.fms.service.FacilityService;
import com.fms.service.MaintenanceService;
import com.fms.service.SessionManager;
import com.fms.service.StaffService;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;
import java.util.List;

public class MaintenanceController {

    @FXML private ComboBox<Facility> facilityCombo;
    @FXML private ComboBox<Equipment> equipmentCombo;
    @FXML private ComboBox<Priority> priorityCombo;
    @FXML private TextArea descriptionArea;

    @FXML private TableView<MaintenanceRequest> requestTable;
    @FXML private TableColumn<MaintenanceRequest, Integer> colId;
    @FXML private TableColumn<MaintenanceRequest, String> colFacility;
    @FXML private TableColumn<MaintenanceRequest, String> colEquipment;
    @FXML private TableColumn<MaintenanceRequest, String> colDescription;
    @FXML private TableColumn<MaintenanceRequest, String> colPriority;
    @FXML private TableColumn<MaintenanceRequest, String> colStatus;
    @FXML private TableColumn<MaintenanceRequest, String> colRaisedBy;
    @FXML private TableColumn<MaintenanceRequest, String> colAssigned;
    @FXML private TableColumn<MaintenanceRequest, String> colScheduled;
    @FXML private TableColumn<MaintenanceRequest, String> colNote;

    @FXML private Button raiseButton;
    @FXML private Button assignButton;
    @FXML private Button startButton;
    @FXML private Button completeButton;

    @FXML private javafx.scene.layout.VBox adminPanel;
    @FXML private javafx.scene.layout.VBox staffPanel;

    @FXML private ComboBox<Staff> staffCombo;
    @FXML private DatePicker scheduledDatePicker;
    @FXML private ComboBox<Integer> startSlotCombo;
    @FXML private ComboBox<Integer> endSlotCombo;
    @FXML private TextField assignmentNoteField;
    @FXML private TextField completionNoteField;
    @FXML private Label messageLabel;

    private final MaintenanceService maintenanceService =
            new MaintenanceService();
    private final FacilityService facilityService =
            new FacilityService();
    private final EquipmentService equipmentService =
            new EquipmentService();
    private final StaffService staffService =
            new StaffService();
    private final SessionManager sessionManager =
            SessionManager.getInstance();

    @FXML
    public void initialize() {
        boolean isAdmin = sessionManager.isAdmin();
        boolean isStaff = sessionManager.isStaff();

        adminPanel.setVisible(isAdmin);
        adminPanel.setManaged(isAdmin);

        staffPanel.setVisible(isAdmin || isStaff);
        staffPanel.setManaged(isAdmin || isStaff);

        setupTable();
        setupForm();
        loadFacilities();
        loadStaff();
        refreshRequests();
    }

    private void setupTable() {
        colId.setCellValueFactory(
                new PropertyValueFactory<>("requestId"));

        colFacility.setCellValueFactory(data ->
                new SimpleStringProperty(
                        safe(data.getValue().getFacilityName())));

        colEquipment.setCellValueFactory(data ->
                new SimpleStringProperty(
                        safe(data.getValue().getEquipmentName())));

        colDescription.setCellValueFactory(
                new PropertyValueFactory<>("description"));

        colPriority.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getPriority().name()));

        colStatus.setCellValueFactory(data ->
                new SimpleStringProperty(
                        data.getValue().getStatus().getDbValue()));

        colRaisedBy.setCellValueFactory(data ->
                new SimpleStringProperty(
                        safe(data.getValue().getRaisedByName())));

        colAssigned.setCellValueFactory(data ->
                new SimpleStringProperty(
                        safe(data.getValue().getAssignedStaffName())));

        colScheduled.setCellValueFactory(data -> {
            MaintenanceRequest request = data.getValue();
            String date = request.getScheduledDate() == null
                    ? "Not scheduled"
                    : request.getScheduledDate().toString();

            return new SimpleStringProperty(date);
        });

        colNote.setCellValueFactory(data ->
                new SimpleStringProperty(
                        safe(data.getValue().getLatestNote())));

        requestTable.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
    }

    private void setupForm() {
        priorityCombo.setItems(
                FXCollections.observableArrayList(Priority.values()));
        priorityCombo.setValue(Priority.Medium);

        startSlotCombo.setItems(
                FXCollections.observableArrayList(
                        1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

        endSlotCombo.setItems(
                FXCollections.observableArrayList(
                        1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

        startSlotCombo.setValue(1);
        endSlotCombo.setValue(1);

        scheduledDatePicker.setValue(LocalDate.now());
        scheduledDatePicker.setDayCellFactory(picker ->
                new DateCell() {
                    @Override
                    public void updateItem(
                            LocalDate date, boolean empty) {
                        super.updateItem(date, empty);

                        if (date.isBefore(LocalDate.now())) {
                            setDisable(true);
                        }
                    }
                });

        facilityCombo.valueProperty().addListener(
                (obs, oldFacility, newFacility) ->
                        loadEquipment(newFacility));
    }

    private void loadFacilities() {
        try {
            List<Facility> facilities =
                    facilityService.getAllFacilities();

            facilities.removeIf(facility ->
                    facility.getStatus() == FacilityStatus.Inactive);

            facilityCombo.setItems(
                    FXCollections.observableArrayList(facilities));

        } catch (Exception e) {
            showMessage("Could not load facilities: " + messageOf(e));
        }
    }

    private void loadEquipment(Facility facility) {
        equipmentCombo.getItems().clear();

        if (facility == null) {
            return;
        }

        try {
            equipmentCombo.setItems(
                    FXCollections.observableArrayList(
                            equipmentService.getEquipmentByFacility(
                                    facility.getFacilityId())));

        } catch (Exception e) {
            showMessage("Could not load equipment: " + messageOf(e));
        }
    }

    private void loadStaff() {
        if (!sessionManager.isAdmin()) {
            return;
        }

        try {
            staffCombo.setItems(
                    FXCollections.observableArrayList(
                            staffService.searchStaff("", null, true)));

        } catch (Exception e) {
            showMessage("Could not load Staff: " + messageOf(e));
        }
    }

    @FXML
    private void handleRaiseRequest() {
        Facility facility = facilityCombo.getValue();
        Priority priority = priorityCombo.getValue();
        String description = descriptionArea.getText();

        if (facility == null) {
            showMessage("Please select a facility.");
            return;
        }

        if (description == null || description.isBlank()) {
            showMessage("Please describe the maintenance problem.");
            return;
        }

        try {
            MaintenanceRequest request = new MaintenanceRequest();
            request.setFacilityId(facility.getFacilityId());

            Equipment equipment = equipmentCombo.getValue();
            if (equipment != null) {
                request.setEquipmentId(equipment.getEquipmentId());
            }

            request.setDescription(description.trim());
            request.setPriority(priority);

            maintenanceService.createRequest(request);

            descriptionArea.clear();
            priorityCombo.setValue(Priority.Medium);
            equipmentCombo.setValue(null);

            refreshRequests();
            showMessage("Maintenance request created successfully.");

        } catch (Exception e) {
            showMessage("Could not create request: " + messageOf(e));
        }
    }

    @FXML
    private void handleAssignRequest() {
        MaintenanceRequest request =
                requestTable.getSelectionModel().getSelectedItem();

        Staff staff = staffCombo.getValue();
        LocalDate date = scheduledDatePicker.getValue();
        Integer start = startSlotCombo.getValue();
        Integer end = endSlotCombo.getValue();

        if (request == null) {
            showMessage("Select a maintenance request first.");
            return;
        }

        if (staff == null || date == null
                || start == null || end == null) {
            showMessage("Select Staff, a date, and a slot range.");
            return;
        }

        try {
            maintenanceService.assignRequest(
                    request.getRequestId(),
                    staff.getStaffId(),
                    date,
                    start,
                    end,
                    assignmentNoteField.getText());

            assignmentNoteField.clear();
            refreshRequests();
            showMessage("Maintenance request assigned successfully.");

        } catch (Exception e) {
            showMessage("Could not assign request: " + messageOf(e));
        }
    }

    @FXML
    private void handleStartWork() {
        updateSelectedRequest(MaintenanceStatus.InProgress, "");
    }

    @FXML
    private void handleCompleteWork() {
        String note = completionNoteField.getText();

        if (note == null || note.isBlank()) {
            showMessage("Enter completion notes before completing the task.");
            return;
        }

        updateSelectedRequest(MaintenanceStatus.Completed, note.trim());
    }

    private void updateSelectedRequest(
            MaintenanceStatus status, String note) {

        MaintenanceRequest request =
                requestTable.getSelectionModel().getSelectedItem();

        if (request == null) {
            showMessage("Select a maintenance request first.");
            return;
        }

        try {
            maintenanceService.updateStatus(
                    request.getRequestId(), status, note);

            completionNoteField.clear();
            refreshRequests();
            showMessage("Maintenance status updated successfully.");

        } catch (Exception e) {
            showMessage("Could not update status: " + messageOf(e));
        }
    }

    @FXML
    private void handleRefresh() {
        refreshRequests();
    }

    private void refreshRequests() {
        try {
            requestTable.setItems(
                    FXCollections.observableArrayList(
                            maintenanceService.getVisibleRequests()));

        } catch (Exception e) {
            showMessage("Could not load requests: " + messageOf(e));
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String messageOf(Exception e) {
        return e.getMessage() == null
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    private void showMessage(String message) {
        messageLabel.setText(message);
    }
}
