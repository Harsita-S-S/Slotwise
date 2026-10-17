package com.fms.controller;

import com.fms.exception.InvalidInputException;
import com.fms.model.Facility;
import com.fms.model.FacilityStatus;
import com.fms.model.FacilityType;
import com.fms.model.Role;
import com.fms.service.FacilityService;
import com.fms.service.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * Controller for Facility Management (Module 2).
 * Supports Dual View: Card Grid and Table View.
 *
 * Covers:
 * - FR-FAC-01: Admin adds facility
 * - FR-FAC-02: Admin edits, deactivates, reactivates facility
 * - FR-FAC-03: Facility status display and transitions
 * - FR-FAC-04: Real-time search and filter toolbar
 * - FR-FAC-05: Duplicate facility prevention feedback
 * - FR-FAC-06: Deactivation protection with future booking warning
 */
public class FacilityController {

    private static final Logger logger = LoggerFactory.getLogger(FacilityController.class);

    // Top action bar
    @FXML private Button addFacilityButton;

    // Filter toolbar
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private ComboBox<String> buildingFilterCombo;
    @FXML private TextField capacityFilterField;
    @FXML private TextField amenityFilterField;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Button resetFiltersButton;

    // View toggle and stats
    @FXML private Label resultCountLabel;
    @FXML private Button cardViewBtn;
    @FXML private Button tableViewBtn;

    // Content views
    @FXML private ScrollPane cardScrollPane;
    @FXML private FlowPane cardContainer;
    @FXML private TableView<Facility> facilityTable;

    // Table columns
    @FXML private TableColumn<Facility, String> colName;
    @FXML private TableColumn<Facility, String> colType;
    @FXML private TableColumn<Facility, String> colBuilding;
    @FXML private TableColumn<Facility, Integer> colFloor;
    @FXML private TableColumn<Facility, Integer> colCapacity;
    @FXML private TableColumn<Facility, String> colAmenities;
    @FXML private TableColumn<Facility, String> colApproval;
    @FXML private TableColumn<Facility, String> colStatus;
    @FXML private TableColumn<Facility, Void> colActions;

    private final FacilityService facilityService;
    private final ObservableList<Facility> facilityList;

    public FacilityController() {
        this.facilityService = new FacilityService();
        this.facilityList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        // Step 1: Check permissions (only Admin can add/edit)
        boolean isAdmin = SessionManager.getInstance().isAdmin();
        addFacilityButton.setVisible(isAdmin);
        addFacilityButton.setManaged(isAdmin);

        // Step 2: Initialize filter dropdowns
        setupFilterDropdowns();

        // Step 3: Setup table view columns
        setupTableColumns();

        // Step 4: Attach listeners for real-time filtering (FR-FAC-04)
        setupFilterListeners();

        // Step 5: Initial load of facilities
        applyFilters();
    }

    // ---------------------------------------------------------------
    // Filter Initialization & Execution
    // ---------------------------------------------------------------

    private void setupFilterDropdowns() {
        // Types
        typeFilterCombo.getItems().clear();
        typeFilterCombo.getItems().add("All Types");
        typeFilterCombo.getItems().add("Classroom");
        typeFilterCombo.getItems().add("Laboratory");
        typeFilterCombo.getItems().add("Hall");
        typeFilterCombo.setValue("All Types");

        // Buildings from database
        refreshBuildingDropdown();

        // Statuses
        statusFilterCombo.getItems().clear();
        statusFilterCombo.getItems().add("All Statuses");
        statusFilterCombo.getItems().add("Available");
        statusFilterCombo.getItems().add("Under Maintenance");
        statusFilterCombo.getItems().add("Inactive");
        statusFilterCombo.setValue("All Statuses");
    }

    private void refreshBuildingDropdown() {
        buildingFilterCombo.getItems().clear();
        buildingFilterCombo.getItems().add("All Buildings");
        List<String> buildings = facilityService.getAllBuildings();
        for (int i = 0; i < buildings.size(); i++) {
            buildingFilterCombo.getItems().add(buildings.get(i));
        }
        buildingFilterCombo.setValue("All Buildings");
    }

    private void setupFilterListeners() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        capacityFilterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        amenityFilterField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());

        typeFilterCombo.setOnAction(e -> applyFilters());
        buildingFilterCombo.setOnAction(e -> applyFilters());
        statusFilterCombo.setOnAction(e -> applyFilters());
    }

    @FXML
    private void handleResetFilters() {
        searchField.clear();
        typeFilterCombo.setValue("All Types");
        buildingFilterCombo.setValue("All Buildings");
        capacityFilterField.clear();
        amenityFilterField.clear();
        statusFilterCombo.setValue("All Statuses");
        applyFilters();
    }

    /**
     * Executes the search and filter query and updates both Card and Table views (FR-FAC-04).
     */
    private void applyFilters() {
        String keyword = searchField.getText();

        // Facility Type
        FacilityType selectedType = null;
        String typeVal = typeFilterCombo.getValue();
        if (typeVal != null && !typeVal.equals("All Types")) {
            selectedType = FacilityType.valueOf(typeVal);
        }

        // Building
        String selectedBuilding = buildingFilterCombo.getValue();
        if (selectedBuilding != null && selectedBuilding.equals("All Buildings")) {
            selectedBuilding = null;
        }

        // Capacity
        Integer minCap = null;
        String capText = capacityFilterField.getText();
        if (capText != null && !capText.trim().isEmpty()) {
            try {
                minCap = Integer.parseInt(capText.trim());
            } catch (NumberFormatException e) {
                // Ignore parse error while typing
            }
        }

        // Amenity
        String amenity = amenityFilterField.getText();

        // Status
        FacilityStatus selectedStatus = null;
        String statusVal = statusFilterCombo.getValue();
        if (statusVal != null && !statusVal.equals("All Statuses")) {
            selectedStatus = FacilityStatus.fromDbValue(statusVal);
        }

        // Fetch from service
        List<Facility> results = facilityService.searchFacilities(
                keyword, selectedType, selectedBuilding, minCap, amenity, selectedStatus
        );

        facilityList.setAll(results);
        resultCountLabel.setText("Showing " + results.size() + " facilities");

        // Render card grid and table
        renderCardGrid(results);
        facilityTable.setItems(facilityList);
    }

    // ---------------------------------------------------------------
    // View Switching: Card Grid vs Table View
    // ---------------------------------------------------------------

    @FXML
    private void handleShowCardView() {
        cardScrollPane.setVisible(true);
        cardScrollPane.setManaged(true);
        facilityTable.setVisible(false);
        facilityTable.setManaged(false);

        cardViewBtn.getStyleClass().remove("view-toggle-btn");
        if (!cardViewBtn.getStyleClass().contains("view-toggle-btn-active")) {
            cardViewBtn.getStyleClass().add("view-toggle-btn-active");
        }

        tableViewBtn.getStyleClass().remove("view-toggle-btn-active");
        if (!tableViewBtn.getStyleClass().contains("view-toggle-btn")) {
            tableViewBtn.getStyleClass().add("view-toggle-btn");
        }
    }

    @FXML
    private void handleShowTableView() {
        cardScrollPane.setVisible(false);
        cardScrollPane.setManaged(false);
        facilityTable.setVisible(true);
        facilityTable.setManaged(true);

        tableViewBtn.getStyleClass().remove("view-toggle-btn");
        if (!tableViewBtn.getStyleClass().contains("view-toggle-btn-active")) {
            tableViewBtn.getStyleClass().add("view-toggle-btn-active");
        }

        cardViewBtn.getStyleClass().remove("view-toggle-btn-active");
        if (!cardViewBtn.getStyleClass().contains("view-toggle-btn")) {
            cardViewBtn.getStyleClass().add("view-toggle-btn");
        }
    }

    // ---------------------------------------------------------------
    // Card Grid Rendering
    // ---------------------------------------------------------------

    private void renderCardGrid(List<Facility> facilities) {
        cardContainer.getChildren().clear();
        boolean isAdmin = SessionManager.getInstance().isAdmin();

        for (int i = 0; i < facilities.size(); i++) {
            Facility facility = facilities.get(i);
            VBox card = createFacilityCard(facility, isAdmin);
            cardContainer.getChildren().add(card);
        }
    }

    private VBox createFacilityCard(Facility facility, boolean isAdmin) {
        VBox card = new VBox(10);
        card.getStyleClass().add("facility-card");
        card.setPrefWidth(310);
        card.setMaxWidth(310);

        // Header: Name + Status Badge
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(facility.getName());
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4A3E3D;");
        nameLabel.setWrapText(true);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label statusBadge = new Label(facility.getStatus().getDbValue());
        statusBadge.getStyleClass().add(getStatusBadgeClass(facility.getStatus()));

        header.getChildren().addAll(nameLabel, statusBadge);

        // Location Info: Building • Floor
        Label locLabel = new Label("📍 " + facility.getBuilding() + "  •  Floor " + facility.getFloor());
        locLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7A6B66;");

        // Type & Capacity Pills Row
        HBox pillsRow = new HBox(8);
        pillsRow.setAlignment(Pos.CENTER_LEFT);

        Label typePill = new Label(facility.getType().name());
        typePill.getStyleClass().add("amenity-tag");

        Label capPill = new Label("👥 " + facility.getCapacity() + " Seats");
        capPill.getStyleClass().add("amenity-tag");

        Label approvalPill = new Label(facility.isRequiresApproval() ? "🛡️ Approval Required" : "⚡ Auto-Approved");
        approvalPill.getStyleClass().add(facility.isRequiresApproval() ? "badge-approval" : "amenity-tag");

        pillsRow.getChildren().addAll(typePill, capPill, approvalPill);

        // Amenities chips
        FlowPane amenitiesPane = new FlowPane(6, 6);
        List<String> amenities = facility.getAmenityList();
        for (int j = 0; j < amenities.size(); j++) {
            Label tag = new Label(amenities.get(j));
            tag.getStyleClass().add("amenity-tag");
            amenitiesPane.getChildren().add(tag);
        }

        // Action Buttons Row (FR-FAC-02, FR-FAC-03, FR-FAC-06)
        HBox actionsRow = new HBox(8);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);
        actionsRow.setStyle("-fx-padding: 8 0 0 0; -fx-border-color: #E5DDD0; -fx-border-width: 1 0 0 0;");

        if (isAdmin) {
            // Edit Button
            Button editBtn = new Button("Edit");
            editBtn.getStyleClass().add("btn-secondary");
            editBtn.setStyle("-fx-padding: 4 10; -fx-font-size: 11px;");
            editBtn.setOnAction(e -> handleOpenEditDialog(facility));

            // Maintenance Toggle Button
            Button maintBtn = new Button(facility.getStatus() == FacilityStatus.Under_Maintenance ? "Set Available" : "Maintenance");
            maintBtn.getStyleClass().add("btn-secondary");
            maintBtn.setStyle("-fx-padding: 4 10; -fx-font-size: 11px;");
            maintBtn.setOnAction(e -> handleToggleMaintenance(facility));

            // Deactivate / Reactivate Button
            Button deactBtn = new Button(facility.getStatus() == FacilityStatus.Inactive ? "Reactivate" : "Deactivate");
            if (facility.getStatus() == FacilityStatus.Inactive) {
                deactBtn.getStyleClass().add("btn-primary");
            } else {
                deactBtn.getStyleClass().add("btn-danger");
            }
            deactBtn.setStyle("-fx-padding: 4 10; -fx-font-size: 11px;");
            deactBtn.setOnAction(e -> handleDeactivateOrReactivate(facility));

            actionsRow.getChildren().addAll(editBtn, maintBtn, deactBtn);
        } else {
            // User / Staff action
            Button bookBtn = new Button("View Schedule");
            bookBtn.getStyleClass().add("btn-secondary");
            bookBtn.setStyle("-fx-padding: 4 12; -fx-font-size: 11px;");
            bookBtn.setDisable(facility.getStatus() == FacilityStatus.Inactive);
            actionsRow.getChildren().add(bookBtn);
        }

        card.getChildren().addAll(header, locLabel, pillsRow, amenitiesPane, actionsRow);
        return card;
    }

    private String getStatusBadgeClass(FacilityStatus status) {
        if (status == FacilityStatus.Available) {
            return "badge-available";
        } else if (status == FacilityStatus.Under_Maintenance) {
            return "badge-maintenance";
        } else {
            return "badge-inactive";
        }
    }

    // ---------------------------------------------------------------
    // Table View Setup
    // ---------------------------------------------------------------

    private void setupTableColumns() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colType.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType().name()));
        colBuilding.setCellValueFactory(new PropertyValueFactory<>("building"));
        colFloor.setCellValueFactory(new PropertyValueFactory<>("floor"));
        colCapacity.setCellValueFactory(new PropertyValueFactory<>("capacity"));
        colAmenities.setCellValueFactory(new PropertyValueFactory<>("amenities"));

        colApproval.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().isRequiresApproval() ? "Required" : "Auto"));

        colStatus.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getStatus().getDbValue()));

        // Actions column with buttons for Admin
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button deactBtn = new Button("Deact");
            private final HBox pane = new HBox(6, editBtn, deactBtn);

            {
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding: 3 8; -fx-font-size: 11px;");
                deactBtn.getStyleClass().add("btn-danger");
                deactBtn.setStyle("-fx-padding: 3 8; -fx-font-size: 11px;");

                editBtn.setOnAction(e -> {
                    Facility f = getTableView().getItems().get(getIndex());
                    handleOpenEditDialog(f);
                });

                deactBtn.setOnAction(e -> {
                    Facility f = getTableView().getItems().get(getIndex());
                    handleDeactivateOrReactivate(f);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || !SessionManager.getInstance().isAdmin()) {
                    setGraphic(null);
                } else {
                    Facility f = getTableView().getItems().get(getIndex());
                    if (f.getStatus() == FacilityStatus.Inactive) {
                        deactBtn.setText("Active");
                        deactBtn.getStyleClass().setAll("btn-primary");
                    } else {
                        deactBtn.setText("Deact");
                        deactBtn.getStyleClass().setAll("btn-danger");
                    }
                    setGraphic(pane);
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // Dialog Actions: Add & Edit Facility (FR-FAC-01, FR-FAC-02, FR-FAC-05)
    // ---------------------------------------------------------------

    @FXML
    public void handleOpenAddDialog() {
        openFacilityDialog(null);
    }

    private void handleOpenEditDialog(Facility facility) {
        openFacilityDialog(facility);
    }

    private void openFacilityDialog(Facility facilityToEdit) {
        try {
            URL fxmlUrl = getClass().getResource("/com/fms/view/facility_dialog.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle(facilityToEdit == null ? "Add New Facility" : "Edit Facility");

            // Attach stylesheet
            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/com/fms/css/slotwise-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            dialogStage.setScene(scene);

            // Lookup dialog controls
            Label title = (Label) root.lookup("#dialogTitle");
            TextField nameField = (TextField) root.lookup("#nameField");
            ComboBox<FacilityType> typeCombo = (ComboBox<FacilityType>) root.lookup("#typeComboBox");
            TextField buildingField = (TextField) root.lookup("#buildingField");
            TextField floorField = (TextField) root.lookup("#floorField");
            TextField capacityField = (TextField) root.lookup("#capacityField");
            TextField amenitiesField = (TextField) root.lookup("#amenitiesField");
            CheckBox approvalCheck = (CheckBox) root.lookup("#requiresApprovalCheckBox");
            Label statusLabel = (Label) root.lookup("#statusLabel");
            ComboBox<FacilityStatus> statusCombo = (ComboBox<FacilityStatus>) root.lookup("#statusComboBox");
            Label errorLabel = (Label) root.lookup("#dialogErrorLabel");
            Button saveBtn = (Button) root.lookup("#saveButton");
            Button cancelBtn = (Button) root.lookup("#cancelButton");

            // Populate types
            typeCombo.getItems().setAll(FacilityType.values());

            // Populate statuses
            statusCombo.getItems().setAll(FacilityStatus.values());

            // Prefill if editing
            if (facilityToEdit != null) {
                title.setText("Edit Facility");
                nameField.setText(facilityToEdit.getName());
                typeCombo.setValue(facilityToEdit.getType());
                buildingField.setText(facilityToEdit.getBuilding());
                floorField.setText(String.valueOf(facilityToEdit.getFloor()));
                capacityField.setText(String.valueOf(facilityToEdit.getCapacity()));
                amenitiesField.setText(facilityToEdit.getAmenities());
                approvalCheck.setSelected(facilityToEdit.isRequiresApproval());
                statusCombo.setValue(facilityToEdit.getStatus());
                saveBtn.setText("Save Changes");
            } else {
                title.setText("Add Facility");
                typeCombo.setValue(FacilityType.Classroom);
                statusLabel.setVisible(false);
                statusLabel.setManaged(false);
                statusCombo.setVisible(false);
                statusCombo.setManaged(false);
                saveBtn.setText("Add Facility");
            }

            cancelBtn.setOnAction(e -> dialogStage.close());

            saveBtn.setOnAction(e -> {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);

                String name = nameField.getText().trim();
                FacilityType type = typeCombo.getValue();
                String building = buildingField.getText().trim();
                String floorStr = floorField.getText().trim();
                String capStr = capacityField.getText().trim();
                String amenities = amenitiesField.getText().trim();
                boolean approval = approvalCheck.isSelected();

                // Basic UI validation
                if (name.isEmpty()) {
                    showDialogError(errorLabel, "Please enter a facility name.");
                    return;
                }

                int floor;
                try {
                    floor = Integer.parseInt(floorStr);
                    if (floor < 0) {
                        showDialogError(errorLabel, "Floor number cannot be negative.");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    showDialogError(errorLabel, "Floor must be a valid integer.");
                    return;
                }

                int capacity;
                try {
                    capacity = Integer.parseInt(capStr);
                    if (capacity <= 0) {
                        showDialogError(errorLabel, "Capacity must be greater than zero.");
                        return;
                    }
                } catch (NumberFormatException ex) {
                    showDialogError(errorLabel, "Capacity must be a valid integer.");
                    return;
                }

                try {
                    if (facilityToEdit == null) {
                        // Create (FR-FAC-01, FR-FAC-05)
                        facilityService.addFacility(name, type, building, floor, capacity, amenities, approval);
                    } else {
                        // Update (FR-FAC-02, FR-FAC-05)
                        facilityToEdit.setName(name);
                        facilityToEdit.setType(type);
                        facilityToEdit.setBuilding(building);
                        facilityToEdit.setFloor(floor);
                        facilityToEdit.setCapacity(capacity);
                        facilityToEdit.setAmenities(amenities);
                        facilityToEdit.setRequiresApproval(approval);
                        if (statusCombo.getValue() != null) {
                            facilityToEdit.setStatus(statusCombo.getValue());
                        }
                        facilityService.updateFacility(facilityToEdit);
                    }

                    dialogStage.close();
                    refreshBuildingDropdown();
                    applyFilters();

                    Alert success = new Alert(Alert.AlertType.INFORMATION);
                    success.setTitle("Facility Saved");
                    success.setHeaderText(facilityToEdit == null ? "Facility Created Successfully!" : "Facility Updated Successfully!");
                    success.setContentText(String.format("Facility '%s' in %s (Floor %d, Capacity: %d) is now live in the system.",
                            name, building, floor, capacity));
                    success.showAndWait();

                } catch (InvalidInputException ex) {
                    showDialogError(errorLabel, ex.getMessage());
                } catch (Exception ex) {
                    logger.error("Failed to save facility: {}", ex.getMessage(), ex);
                    showDialogError(errorLabel, "Failed to save facility: " + ex.getMessage());
                }
            });

            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening facility dialog: {}", e.getMessage(), e);
        }
    }

    private void showDialogError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    // ---------------------------------------------------------------
    // Status & Deactivation Handlers (FR-FAC-02, FR-FAC-03, FR-FAC-06)
    // ---------------------------------------------------------------

    private void handleToggleMaintenance(Facility facility) {
        FacilityStatus newStatus = (facility.getStatus() == FacilityStatus.Under_Maintenance)
                ? FacilityStatus.Available
                : FacilityStatus.Under_Maintenance;

        facilityService.changeFacilityStatus(facility.getFacilityId(), newStatus);
        applyFilters();
    }

    private void handleDeactivateOrReactivate(Facility facility) {
        if (facility.getStatus() == FacilityStatus.Inactive) {
            // Reactivate
            facilityService.reactivateFacility(facility.getFacilityId());
            applyFilters();
            return;
        }

        // Deactivate flow with safety check (FR-FAC-06)
        FacilityService.DeactivationResult result =
                facilityService.deactivateFacility(facility.getFacilityId(), false);

        if (result.isConfirmationRequired()) {
            // Prompt Admin for confirmation
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirm Facility Deactivation");
            alert.setHeaderText("Upcoming Bookings Warning (FR-FAC-06)");
            alert.setContentText(result.getMessage() + "\n\n" +
                    "Deactivating will automatically cancel these bookings and notify affected users.\n" +
                    "Do you want to proceed?");

            Optional<ButtonType> choice = alert.showAndWait();
            if (choice.isPresent() && choice.get() == ButtonType.OK) {
                // Confirm deactivation
                facilityService.deactivateFacility(facility.getFacilityId(), true);
                applyFilters();
            }
        } else if (result.isSuccess()) {
            applyFilters();
        }
    }
}
