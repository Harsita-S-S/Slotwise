package com.fms.controller;

import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import com.fms.service.EquipmentService;
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
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Controller for Equipment Management (Module 3).
 * Supports Dual View: Card Grid and Table View.
 *
 * Covers:
 * - FR-EQP-01: Admin/Staff adds/edits equipment with facility linkage.
 * - FR-EQP-02: Condition tracking (Good, Needs Repair, Under Repair, Retired).
 * - FR-EQP-03: Filter by facility and move equipment to another facility.
 * - FR-EQP-04: View maintenance history records.
 */
public class EquipmentController {

    private static final Logger logger = LoggerFactory.getLogger(EquipmentController.class);

    // Header & Actions
    @FXML private Button addEquipmentButton;

    // Filters (FR-EQP-03)
    @FXML private TextField searchField;
    @FXML private ComboBox<String> facilityFilterCombo;
    @FXML private ComboBox<String> categoryFilterCombo;
    @FXML private ComboBox<String> conditionFilterCombo;
    @FXML private Button resetFiltersButton;

    // Results summary & view toggle
    @FXML private Label resultCountLabel;
    @FXML private Button cardViewBtn;
    @FXML private Button tableViewBtn;

    // View containers
    @FXML private ScrollPane cardScrollPane;
    @FXML private FlowPane cardContainer;
    @FXML private TableView<Equipment> equipmentTable;

    // Table columns
    @FXML private TableColumn<Equipment, String> colName;
    @FXML private TableColumn<Equipment, String> colSerial;
    @FXML private TableColumn<Equipment, String> colCategory;
    @FXML private TableColumn<Equipment, String> colFacility;
    @FXML private TableColumn<Equipment, String> colPurchaseDate;
    @FXML private TableColumn<Equipment, String> colCondition;
    @FXML private TableColumn<Equipment, Void> colActions;

    private final EquipmentService equipmentService;
    private final FacilityService facilityService;
    private final ObservableList<Equipment> equipmentList;

    // Cache of facilities for mapping dropdowns
    private List<Facility> cachedFacilities;

    public EquipmentController() {
        this.equipmentService = new EquipmentService();
        this.facilityService = new FacilityService();
        this.equipmentList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        // Step 1: Check permissions (Admin or Staff can add/manage equipment)
        boolean canManage = SessionManager.getInstance().isStaff() || SessionManager.getInstance().isAdmin();
        addEquipmentButton.setVisible(canManage);
        addEquipmentButton.setManaged(canManage);

        // Step 2: Populate filter dropdowns
        setupFilterDropdowns();

        // Step 3: Configure TableView columns
        setupTableColumns();

        // Step 4: Real-time search/filter listeners
        setupFilterListeners();

        // Step 5: Initial query
        applyFilters();
    }

    // ---------------------------------------------------------------
    // Filter Setup & Execution
    // ---------------------------------------------------------------

    private void setupFilterDropdowns() {
        // Facilities dropdown
        refreshFacilityDropdown();

        // Categories dropdown
        refreshCategoryDropdown();

        // Conditions dropdown (FR-EQP-02)
        conditionFilterCombo.getItems().clear();
        conditionFilterCombo.getItems().add("All Conditions");
        conditionFilterCombo.getItems().add("Good");
        conditionFilterCombo.getItems().add("Needs Repair");
        conditionFilterCombo.getItems().add("Under Repair");
        conditionFilterCombo.getItems().add("Retired");
        conditionFilterCombo.setValue("All Conditions");
    }

    private void refreshFacilityDropdown() {
        cachedFacilities = facilityService.getAllFacilities();
        facilityFilterCombo.getItems().clear();
        facilityFilterCombo.getItems().add("All Facilities");
        for (int i = 0; i < cachedFacilities.size(); i++) {
            facilityFilterCombo.getItems().add(cachedFacilities.get(i).getName());
        }
        facilityFilterCombo.setValue("All Facilities");
    }

    private void refreshCategoryDropdown() {
        categoryFilterCombo.getItems().clear();
        categoryFilterCombo.getItems().add("All Categories");
        List<String> categories = equipmentService.getAllCategories();
        for (int i = 0; i < categories.size(); i++) {
            categoryFilterCombo.getItems().add(categories.get(i));
        }
        categoryFilterCombo.setValue("All Categories");
    }

    private void setupFilterListeners() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        facilityFilterCombo.setOnAction(e -> applyFilters());
        categoryFilterCombo.setOnAction(e -> applyFilters());
        conditionFilterCombo.setOnAction(e -> applyFilters());
    }

    @FXML
    private void handleResetFilters() {
        searchField.clear();
        facilityFilterCombo.setValue("All Facilities");
        categoryFilterCombo.setValue("All Categories");
        conditionFilterCombo.setValue("All Conditions");
        applyFilters();
    }

    /**
     * Executes the search and filter query across facilities, categories, and conditions (FR-EQP-03).
     */
    private void applyFilters() {
        String keyword = searchField.getText();

        // Match facility ID from selected facility name
        Integer facilityId = null;
        String facVal = facilityFilterCombo.getValue();
        if (facVal != null && !facVal.equals("All Facilities") && cachedFacilities != null) {
            for (int i = 0; i < cachedFacilities.size(); i++) {
                if (cachedFacilities.get(i).getName().equalsIgnoreCase(facVal)) {
                    facilityId = cachedFacilities.get(i).getFacilityId();
                    break;
                }
            }
        }

        // Category
        String category = categoryFilterCombo.getValue();
        if (category != null && category.equals("All Categories")) {
            category = null;
        }

        // Condition
        EquipmentCondition condition = null;
        String condVal = conditionFilterCombo.getValue();
        if (condVal != null && !condVal.equals("All Conditions")) {
            condition = EquipmentCondition.fromDbValue(condVal);
        }

        List<Equipment> results = equipmentService.searchEquipment(keyword, facilityId, category, condition);
        equipmentList.setAll(results);
        resultCountLabel.setText("Showing " + results.size() + " equipment items");

        renderCardGrid(results);
        equipmentTable.setItems(equipmentList);
    }

    // ---------------------------------------------------------------
    // View Switching: Card Grid vs Table View
    // ---------------------------------------------------------------

    @FXML
    private void handleShowCardView() {
        cardScrollPane.setVisible(true);
        cardScrollPane.setManaged(true);
        equipmentTable.setVisible(false);
        equipmentTable.setManaged(false);

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
        equipmentTable.setVisible(true);
        equipmentTable.setManaged(true);

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

    private void renderCardGrid(List<Equipment> items) {
        cardContainer.getChildren().clear();
        boolean canManage = SessionManager.getInstance().isStaff() || SessionManager.getInstance().isAdmin();

        for (int i = 0; i < items.size(); i++) {
            Equipment eq = items.get(i);
            VBox card = createEquipmentCard(eq, canManage);
            cardContainer.getChildren().add(card);
        }
    }

    private VBox createEquipmentCard(Equipment eq, boolean canManage) {
        VBox card = new VBox(10);
        card.getStyleClass().add("facility-card");
        card.setPrefWidth(310);
        card.setMaxWidth(310);

        // Header: Name + Condition Badge (FR-EQP-02)
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(eq.getName());
        nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #4A3E3D;");
        nameLabel.setWrapText(true);
        HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);

        Label condBadge = new Label(eq.getConditionStatus().getDbValue());
        condBadge.getStyleClass().add(getConditionBadgeClass(eq.getConditionStatus()));

        header.getChildren().addAll(nameLabel, condBadge);

        // Serial Number & Category row
        HBox pillsRow = new HBox(6);
        pillsRow.setAlignment(Pos.CENTER_LEFT);

        Label serialPill = new Label("SN: " + eq.getSerialNo());
        serialPill.getStyleClass().add("amenity-tag");

        Label catPill = new Label(eq.getCategory());
        catPill.getStyleClass().add("amenity-tag");

        pillsRow.getChildren().addAll(serialPill, catPill);

        // Location Info
        String locName = (eq.getFacilityName() != null) ? eq.getFacilityName() : "Facility #" + eq.getFacilityId();
        Label locLabel = new Label("📍 Location: " + locName);
        locLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #7A6B66;");

        // Purchase date
        Label dateLabel = new Label("📅 Purchased: " + eq.getPurchaseDate().toString());
        dateLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #A09490;");

        // Action Buttons Row (FR-EQP-02, FR-EQP-03, FR-EQP-04)
        HBox actionsRow = new HBox(6);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);
        actionsRow.setStyle("-fx-padding: 8 0 0 0; -fx-border-color: #E5DDD0; -fx-border-width: 1 0 0 0;");

        // 1. Maintenance History Button (Available to all)
        Button histBtn = new Button("History");
        histBtn.getStyleClass().add("btn-secondary");
        histBtn.setStyle("-fx-padding: 4 8; -fx-font-size: 11px;");
        histBtn.setOnAction(e -> handleOpenHistoryDialog(eq));
        actionsRow.getChildren().add(histBtn);

        if (canManage) {
            // 2. Relocate / Move Button (FR-EQP-03)
            Button moveBtn = new Button("Relocate");
            moveBtn.getStyleClass().add("btn-secondary");
            moveBtn.setStyle("-fx-padding: 4 8; -fx-font-size: 11px;");
            moveBtn.setOnAction(e -> handleOpenMoveDialog(eq));

            // 3. Condition Update Menu (FR-EQP-02)
            MenuButton condBtn = new MenuButton("Status");
            condBtn.getStyleClass().add("btn-secondary");
            condBtn.setStyle("-fx-padding: 4 6; -fx-font-size: 11px;");

            EquipmentCondition[] allConds = EquipmentCondition.values();
            for (int k = 0; k < allConds.length; k++) {
                final EquipmentCondition c = allConds[k];
                MenuItem item = new MenuItem(c.getDbValue());
                item.setOnAction(ev -> {
                    equipmentService.updateCondition(eq.getEquipmentId(), c);
                    applyFilters();
                });
                condBtn.getItems().add(item);
            }

            // 4. Edit Button
            Button editBtn = new Button("Edit");
            editBtn.getStyleClass().add("btn-secondary");
            editBtn.setStyle("-fx-padding: 4 8; -fx-font-size: 11px;");
            editBtn.setOnAction(e -> handleOpenEditDialog(eq));

            actionsRow.getChildren().addAll(moveBtn, condBtn, editBtn);
        }

        card.getChildren().addAll(header, pillsRow, locLabel, dateLabel, actionsRow);
        return card;
    }

    private String getConditionBadgeClass(EquipmentCondition condition) {
        if (condition == EquipmentCondition.Good) {
            return "badge-good";
        } else if (condition == EquipmentCondition.NeedsRepair) {
            return "badge-needs-repair";
        } else if (condition == EquipmentCondition.UnderRepair) {
            return "badge-under-repair";
        } else {
            return "badge-retired";
        }
    }

    // ---------------------------------------------------------------
    // Table View Setup
    // ---------------------------------------------------------------

    private void setupTableColumns() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colSerial.setCellValueFactory(new PropertyValueFactory<>("serialNo"));
        colCategory.setCellValueFactory(new PropertyValueFactory<>("category"));

        colFacility.setCellValueFactory(cellData -> {
            String name = cellData.getValue().getFacilityName();
            return new SimpleStringProperty(name != null ? name : "Facility #" + cellData.getValue().getFacilityId());
        });

        colPurchaseDate.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getPurchaseDate().toString()));

        colCondition.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getConditionStatus().getDbValue()));

        // Actions column
        colActions.setCellFactory(param -> new TableCell<>() {
            private final Button histBtn = new Button("History");
            private final Button moveBtn = new Button("Move");
            private final Button editBtn = new Button("Edit");
            private final HBox pane = new HBox(6, histBtn, moveBtn, editBtn);

            {
                histBtn.getStyleClass().add("btn-secondary");
                histBtn.setStyle("-fx-padding: 3 8; -fx-font-size: 11px;");
                moveBtn.getStyleClass().add("btn-secondary");
                moveBtn.setStyle("-fx-padding: 3 8; -fx-font-size: 11px;");
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-padding: 3 8; -fx-font-size: 11px;");

                histBtn.setOnAction(e -> {
                    Equipment eq = getTableView().getItems().get(getIndex());
                    handleOpenHistoryDialog(eq);
                });

                moveBtn.setOnAction(e -> {
                    Equipment eq = getTableView().getItems().get(getIndex());
                    handleOpenMoveDialog(eq);
                });

                editBtn.setOnAction(e -> {
                    Equipment eq = getTableView().getItems().get(getIndex());
                    handleOpenEditDialog(eq);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    boolean canManage = SessionManager.getInstance().isStaff() || SessionManager.getInstance().isAdmin();
                    moveBtn.setVisible(canManage);
                    moveBtn.setManaged(canManage);
                    editBtn.setVisible(canManage);
                    editBtn.setManaged(canManage);
                    setGraphic(pane);
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // Add & Edit Equipment Dialogs (FR-EQP-01)
    // ---------------------------------------------------------------

    @FXML
    public void handleOpenAddDialog() {
        openEquipmentDialog(null);
    }

    private void handleOpenEditDialog(Equipment eqToEdit) {
        openEquipmentDialog(eqToEdit);
    }

    private void openEquipmentDialog(Equipment eqToEdit) {
        try {
            URL fxmlUrl = getClass().getResource("/com/fms/view/equipment_dialog.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle(eqToEdit == null ? "Add New Equipment" : "Edit Equipment");

            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/com/fms/css/slotwise-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            dialogStage.setScene(scene);

            // Controls
            Label title = (Label) root.lookup("#dialogTitle");
            ComboBox<Facility> facilityCombo = (ComboBox<Facility>) root.lookup("#facilityComboBox");
            TextField nameField = (TextField) root.lookup("#nameField");
            TextField categoryField = (TextField) root.lookup("#categoryField");
            TextField serialField = (TextField) root.lookup("#serialField");
            DatePicker purchaseDatePicker = (DatePicker) root.lookup("#purchaseDatePicker");
            Label conditionLabel = (Label) root.lookup("#conditionLabel");
            ComboBox<EquipmentCondition> conditionCombo = (ComboBox<EquipmentCondition>) root.lookup("#conditionComboBox");
            Label errorLabel = (Label) root.lookup("#dialogErrorLabel");
            Button saveBtn = (Button) root.lookup("#saveButton");
            Button cancelBtn = (Button) root.lookup("#cancelButton");

            // Populate facilities
            List<Facility> facilities = facilityService.getActiveFacilities();
            facilityCombo.getItems().setAll(facilities);

            // Populate condition enum
            conditionCombo.getItems().setAll(EquipmentCondition.values());

            // Prefill
            if (eqToEdit != null) {
                title.setText("Edit Equipment");
                nameField.setText(eqToEdit.getName());
                categoryField.setText(eqToEdit.getCategory());
                serialField.setText(eqToEdit.getSerialNo());
                purchaseDatePicker.setValue(eqToEdit.getPurchaseDate().toLocalDate());
                conditionCombo.setValue(eqToEdit.getConditionStatus());
                saveBtn.setText("Save Changes");

                // Select current facility
                for (int i = 0; i < facilities.size(); i++) {
                    if (facilities.get(i).getFacilityId() == eqToEdit.getFacilityId()) {
                        facilityCombo.setValue(facilities.get(i));
                        break;
                    }
                }
            } else {
                title.setText("Add Equipment");
                purchaseDatePicker.setValue(LocalDate.now());
                conditionLabel.setVisible(false);
                conditionLabel.setManaged(false);
                conditionCombo.setVisible(false);
                conditionCombo.setManaged(false);
                saveBtn.setText("Add Equipment");
            }

            cancelBtn.setOnAction(e -> dialogStage.close());

            saveBtn.setOnAction(e -> {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);

                Facility selectedFac = facilityCombo.getValue();
                String name = nameField.getText().trim();
                String category = categoryField.getText().trim();
                String serial = serialField.getText().trim();
                LocalDate dateVal = purchaseDatePicker.getValue();

                if (selectedFac == null) {
                    showDialogError(errorLabel, "Please select a facility space.");
                    return;
                }

                if (dateVal == null) {
                    showDialogError(errorLabel, "Please select a valid purchase date.");
                    return;
                }

                Date purchaseSqlDate = Date.valueOf(dateVal);

                try {
                    if (eqToEdit == null) {
                        equipmentService.addEquipment(selectedFac.getFacilityId(), name, category, serial, purchaseSqlDate);
                    } else {
                        eqToEdit.setFacilityId(selectedFac.getFacilityId());
                        eqToEdit.setName(name);
                        eqToEdit.setCategory(category);
                        eqToEdit.setSerialNo(serial);
                        eqToEdit.setPurchaseDate(purchaseSqlDate);
                        if (conditionCombo.getValue() != null) {
                            eqToEdit.setConditionStatus(conditionCombo.getValue());
                        }
                        equipmentService.updateEquipment(eqToEdit);
                    }

                    dialogStage.close();
                    refreshCategoryDropdown();
                    applyFilters();

                } catch (InvalidInputException ex) {
                    showDialogError(errorLabel, ex.getMessage());
                } catch (Exception ex) {
                    logger.error("Error saving equipment: {}", ex.getMessage(), ex);
                    showDialogError(errorLabel, "Failed to save equipment: " + ex.getMessage());
                }
            });

            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening equipment dialog: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // Relocate Equipment Dialog (FR-EQP-03)
    // ---------------------------------------------------------------

    private void handleOpenMoveDialog(Equipment eq) {
        try {
            URL fxmlUrl = getClass().getResource("/com/fms/view/equipment_move_dialog.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Relocate Equipment Asset");

            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/com/fms/css/slotwise-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            dialogStage.setScene(scene);

            Label summaryLabel = (Label) root.lookup("#equipmentSummaryLabel");
            Label curFacLabel = (Label) root.lookup("#currentFacilityLabel");
            ComboBox<Facility> destCombo = (ComboBox<Facility>) root.lookup("#destinationFacilityCombo");
            Label errorLabel = (Label) root.lookup("#moveErrorLabel");
            Button cancelBtn = (Button) root.lookup("#cancelButton");
            Button confirmBtn = (Button) root.lookup("#confirmMoveButton");

            summaryLabel.setText("Equipment: " + eq.getName() + " [" + eq.getSerialNo() + "]");
            curFacLabel.setText("Current Location: " + (eq.getFacilityName() != null ? eq.getFacilityName() : "Facility #" + eq.getFacilityId()));

            // Populate other facilities
            List<Facility> allFacs = facilityService.getActiveFacilities();
            List<Facility> destinations = new ArrayList<>();
            for (int i = 0; i < allFacs.size(); i++) {
                if (allFacs.get(i).getFacilityId() != eq.getFacilityId()) {
                    destinations.add(allFacs.get(i));
                }
            }
            destCombo.getItems().setAll(destinations);

            cancelBtn.setOnAction(e -> dialogStage.close());

            confirmBtn.setOnAction(e -> {
                errorLabel.setVisible(false);
                errorLabel.setManaged(false);

                Facility dest = destCombo.getValue();
                if (dest == null) {
                    showDialogError(errorLabel, "Please select a destination facility.");
                    return;
                }

                try {
                    equipmentService.moveEquipment(eq.getEquipmentId(), dest.getFacilityId());
                    dialogStage.close();
                    applyFilters();
                } catch (InvalidInputException ex) {
                    showDialogError(errorLabel, ex.getMessage());
                } catch (Exception ex) {
                    logger.error("Failed to move equipment: {}", ex.getMessage(), ex);
                    showDialogError(errorLabel, "Failed to move equipment: " + ex.getMessage());
                }
            });

            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening move equipment dialog: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // Maintenance History Dialog (FR-EQP-04)
    // ---------------------------------------------------------------

    private void handleOpenHistoryDialog(Equipment eq) {
        try {
            URL fxmlUrl = getClass().getResource("/com/fms/view/equipment_history_dialog.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Maintenance History");

            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/com/fms/css/slotwise-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            dialogStage.setScene(scene);

            Label titleLabel = (Label) root.lookup("#historyTitleLabel");
            Label subLabel = (Label) root.lookup("#historySubtitleLabel");
            TableView<MaintenanceHistoryItem> table = (TableView<MaintenanceHistoryItem>) root.lookup("#historyTable");
            Button closeBtn = (Button) root.lookup("#closeButton");

            titleLabel.setText("Maintenance History: " + eq.getName());
            subLabel.setText("Serial Number: " + eq.getSerialNo() + "  •  Category: " + eq.getCategory());

            TableColumn<MaintenanceHistoryItem, Integer> colId = (TableColumn<MaintenanceHistoryItem, Integer>) table.getColumns().get(0);
            TableColumn<MaintenanceHistoryItem, String> colDate = (TableColumn<MaintenanceHistoryItem, String>) table.getColumns().get(1);
            TableColumn<MaintenanceHistoryItem, String> colDesc = (TableColumn<MaintenanceHistoryItem, String>) table.getColumns().get(2);
            TableColumn<MaintenanceHistoryItem, String> colPri = (TableColumn<MaintenanceHistoryItem, String>) table.getColumns().get(3);
            TableColumn<MaintenanceHistoryItem, String> colStat = (TableColumn<MaintenanceHistoryItem, String>) table.getColumns().get(4);
            TableColumn<MaintenanceHistoryItem, String> colStaff = (TableColumn<MaintenanceHistoryItem, String>) table.getColumns().get(5);

            colId.setCellValueFactory(new PropertyValueFactory<>("requestId"));
            colDate.setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getCreatedAt() != null ?
                            cellData.getValue().getCreatedAt().toString().substring(0, 10) : "N/A"));
            colDesc.setCellValueFactory(new PropertyValueFactory<>("description"));
            colPri.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getPriority().name()));
            colStat.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getStatus().name()));
            colStaff.setCellValueFactory(cellData -> {
                String s = cellData.getValue().getStaffName();
                return new SimpleStringProperty(s != null ? s : "Unassigned");
            });

            List<MaintenanceHistoryItem> history = equipmentService.getMaintenanceHistory(eq.getEquipmentId());
            table.setItems(FXCollections.observableArrayList(history));

            closeBtn.setOnAction(e -> dialogStage.close());

            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Error opening equipment history dialog: {}", e.getMessage(), e);
        }
    }

    private void showDialogError(Label errorLabel, String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}
