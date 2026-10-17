package com.fms.controller;

import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import com.fms.service.SessionManager;
import com.fms.service.StaffService;
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
 * Controller for Staff Management (Module 4).
 * Supports Dual View: Card Grid and Table View.
 *
 * Covers:
 * - FR-STF-01: Admin adds, edits, and deactivates staff records with user linkage.
 * - FR-STF-02: Specializations: Electrician, Lab Technician, Plumber, IT Support, General.
 * - FR-STF-03: Workload tracking and open maintenance tasks inspector.
 */
public class StaffController {

    private static final Logger logger = LoggerFactory.getLogger(StaffController.class);

    // Header & Actions
    @FXML private Button addStaffButton;

    // Filter controls
    @FXML private TextField searchField;
    @FXML private ComboBox<String> specializationFilterCombo;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Button resetFiltersButton;

    // View toggle & counts
    @FXML private Label resultCountLabel;
    @FXML private Button cardViewBtn;
    @FXML private Button tableViewBtn;

    // Views
    @FXML private ScrollPane cardScrollPane;
    @FXML private FlowPane cardContainer;
    @FXML private TableView<Staff> staffTable;

    // Table columns
    @FXML private TableColumn<Staff, String> colName;
    @FXML private TableColumn<Staff, String> colTrade;
    @FXML private TableColumn<Staff, String> colPhone;
    @FXML private TableColumn<Staff, String> colStatus;
    @FXML private TableColumn<Staff, String> colWorkload;
    @FXML private TableColumn<Staff, Void> colActions;

    private final StaffService staffService;
    private final ObservableList<Staff> staffList;

    public StaffController() {
        this.staffService = new StaffService();
        this.staffList = FXCollections.observableArrayList();
    }

    @FXML
    public void initialize() {
        // Step 1: Admin-only permissions for adding new staff
        boolean isAdmin = SessionManager.getInstance().isAdmin();
        addStaffButton.setVisible(isAdmin);
        addStaffButton.setManaged(isAdmin);

        // Step 2: Set up filter dropdowns
        setupFilterDropdowns();

        // Step 3: Set up table columns
        setupTableColumns();

        // Step 4: Real-time filter listeners
        setupFilterListeners();

        // Step 5: Initial query
        applyFilters();
    }

    // ---------------------------------------------------------------
    // Filter Setup & Execution
    // ---------------------------------------------------------------

    private void setupFilterDropdowns() {
        // Specialization dropdown
        specializationFilterCombo.getItems().clear();
        specializationFilterCombo.getItems().add("All Specializations");
        for (StaffSpecialization spec : StaffSpecialization.values()) {
            specializationFilterCombo.getItems().add(spec.getDisplayName());
        }
        specializationFilterCombo.setValue("All Specializations");

        // Status dropdown
        statusFilterCombo.getItems().clear();
        statusFilterCombo.getItems().add("All Statuses");
        statusFilterCombo.getItems().add("Active Only");
        statusFilterCombo.getItems().add("Inactive Only");
        statusFilterCombo.setValue("All Statuses");
    }

    private void setupFilterListeners() {
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
        specializationFilterCombo.setOnAction(e -> applyFilters());
        statusFilterCombo.setOnAction(e -> applyFilters());
    }

    @FXML
    private void handleResetFilters() {
        searchField.clear();
        specializationFilterCombo.setValue("All Specializations");
        statusFilterCombo.setValue("All Statuses");
        applyFilters();
    }

    private void applyFilters() {
        String keyword = searchField.getText();

        // Specialization
        StaffSpecialization specialization = null;
        String specVal = specializationFilterCombo.getValue();
        if (specVal != null && !specVal.equals("All Specializations")) {
            specialization = StaffSpecialization.fromString(specVal);
        }

        // Status
        Boolean activeOnly = null;
        String statusVal = statusFilterCombo.getValue();
        if ("Active Only".equals(statusVal)) {
            activeOnly = true;
        } else if ("Inactive Only".equals(statusVal)) {
            activeOnly = false;
        }

        List<Staff> results = staffService.searchStaff(keyword, specialization, activeOnly);
        staffList.setAll(results);
        resultCountLabel.setText("Showing " + results.size() + " staff members");

        renderCardGrid(results);
        staffTable.setItems(staffList);
    }

    // ---------------------------------------------------------------
    // View Switching: Card Grid vs Table View
    // ---------------------------------------------------------------

    @FXML
    private void handleShowCardView() {
        cardScrollPane.setVisible(true);
        cardScrollPane.setManaged(true);
        staffTable.setVisible(false);
        staffTable.setManaged(false);

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
        staffTable.setVisible(true);
        staffTable.setManaged(true);

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
    // Card Grid Rendering (FR-STF-01, FR-STF-02, FR-STF-03)
    // ---------------------------------------------------------------

    private void renderCardGrid(List<Staff> items) {
        cardContainer.getChildren().clear();
        boolean isAdmin = SessionManager.getInstance().isAdmin();

        for (int i = 0; i < items.size(); i++) {
            Staff staff = items.get(i);
            int openTasks = staff.getOpenTaskCount();

            VBox card = new VBox(10);
            card.getStyleClass().add("facility-card");
            card.setPrefWidth(300);

            // Card Header: Staff Name + Trade Badge
            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);

            Label nameLabel = new Label(staff.getFullName() != null ? staff.getFullName() : "Staff #" + staff.getStaffId());
            nameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #4A3E3D;");
            HBox.setHgrow(nameLabel, javafx.scene.layout.Priority.ALWAYS);

            Label tradeBadge = createTradeBadge(staff.getSpecialization());
            topRow.getChildren().addAll(nameLabel, tradeBadge);

            // Details section: Phone and Email
            VBox detailsBox = new VBox(4);
            Label phoneLabel = new Label("📞 " + staff.getPhone());
            phoneLabel.setStyle("-fx-text-fill: #7A6B66; -fx-font-size: 12px;");

            Label emailLabel = new Label("✉️ " + (staff.getEmail() != null ? staff.getEmail() : "No email linked"));
            emailLabel.setStyle("-fx-text-fill: #7A6B66; -fx-font-size: 12px;");

            detailsBox.getChildren().addAll(phoneLabel, emailLabel);

            // Workload Indicator (FR-STF-03)
            HBox workloadRow = new HBox(8);
            workloadRow.setAlignment(Pos.CENTER_LEFT);
            Label workloadTitle = new Label("Workload:");
            workloadTitle.setStyle("-fx-text-fill: #4A3E3D; -fx-font-weight: bold; -fx-font-size: 11px;");

            Label workloadBadge = createWorkloadBadge(openTasks);
            workloadRow.getChildren().addAll(workloadTitle, workloadBadge);

            // Status label if inactive
            if (!staff.isActive()) {
                Label inactiveBadge = new Label("INACTIVE");
                inactiveBadge.getStyleClass().add("badge-inactive");
                workloadRow.getChildren().add(inactiveBadge);
            }

            // Separator
            Region divider = new Region();
            divider.setPrefHeight(1);
            divider.setStyle("-fx-background-color: #E5DDD0;");

            // Action Buttons Row
            HBox actionRow = new HBox(8);
            actionRow.setAlignment(Pos.CENTER_LEFT);

            Button viewTasksBtn = new Button("📋 Tasks (" + openTasks + ")");
            viewTasksBtn.getStyleClass().add("btn-secondary");
            viewTasksBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
            viewTasksBtn.setOnAction(e -> handleOpenTasksDialog(staff));
            actionRow.getChildren().add(viewTasksBtn);

            if (isAdmin) {
                Button editBtn = new Button("✏️ Edit");
                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
                editBtn.setOnAction(e -> handleOpenEditDialog(staff));
                actionRow.getChildren().add(editBtn);

                Button toggleStatusBtn = new Button(staff.isActive() ? "Deactivate" : "Reactivate");
                toggleStatusBtn.getStyleClass().add(staff.isActive() ? "btn-secondary" : "btn-primary");
                toggleStatusBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
                toggleStatusBtn.setOnAction(e -> handleToggleActive(staff));
                actionRow.getChildren().add(toggleStatusBtn);
            }

            card.getChildren().addAll(topRow, detailsBox, workloadRow, divider, actionRow);
            cardContainer.getChildren().add(card);
        }
    }

    private Label createTradeBadge(StaffSpecialization spec) {
        Label badge = new Label();
        if (spec == null) {
            badge.setText("General");
            badge.getStyleClass().add("badge-trade-general");
            return badge;
        }

        switch (spec) {
            case Electrician:
                badge.setText("⚡ Electrician");
                badge.getStyleClass().add("badge-trade-electrician");
                break;
            case Plumber:
                badge.setText("🔧 Plumber");
                badge.getStyleClass().add("badge-trade-plumber");
                break;
            case ITSupport:
                badge.setText("💻 IT Support");
                badge.getStyleClass().add("badge-trade-itsupport");
                break;
            case LabTechnician:
                badge.setText("🔬 Lab Tech");
                badge.getStyleClass().add("badge-trade-labtech");
                break;
            case General:
            default:
                badge.setText("🛠 General");
                badge.getStyleClass().add("badge-trade-general");
                break;
        }
        return badge;
    }

    private Label createWorkloadBadge(int taskCount) {
        Label badge = new Label();
        if (taskCount == 0) {
            badge.setText("Available (0)");
            badge.getStyleClass().add("badge-workload-available");
        } else if (taskCount <= 3) {
            badge.setText("Moderate (" + taskCount + ")");
            badge.getStyleClass().add("badge-workload-moderate");
        } else {
            badge.setText("Busy (" + taskCount + ")");
            badge.getStyleClass().add("badge-workload-busy");
        }
        return badge;
    }

    // ---------------------------------------------------------------
    // TableView Configuration
    // ---------------------------------------------------------------

    private void setupTableColumns() {
        colName.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getFullName()));

        colTrade.setCellValueFactory(cellData -> {
            StaffSpecialization s = cellData.getValue().getSpecialization();
            return new SimpleStringProperty(s != null ? s.getDisplayName() : "General");
        });

        colPhone.setCellValueFactory(new PropertyValueFactory<>("phone"));

        colStatus.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().isActive() ? "Active" : "Inactive"));

        colWorkload.setCellValueFactory(cellData -> {
            int tasks = cellData.getValue().getOpenTaskCount();
            return new SimpleStringProperty(tasks + " open tasks");
        });

        setupTableActionButtons();
    }

    private void setupTableActionButtons() {
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button tasksBtn = new Button("Tasks");
            private final Button editBtn = new Button("Edit");
            private final Button toggleBtn = new Button();
            private final HBox btnBox = new HBox(6, tasksBtn, editBtn, toggleBtn);

            {
                tasksBtn.getStyleClass().add("btn-secondary");
                tasksBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8;");

                editBtn.getStyleClass().add("btn-secondary");
                editBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8;");

                toggleBtn.setStyle("-fx-font-size: 11px; -fx-padding: 3 8;");
                btnBox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Staff staff = getTableRow().getItem();
                    boolean isAdmin = SessionManager.getInstance().isAdmin();

                    tasksBtn.setOnAction(e -> handleOpenTasksDialog(staff));

                    if (isAdmin) {
                        editBtn.setVisible(true);
                        editBtn.setManaged(true);
                        editBtn.setOnAction(e -> handleOpenEditDialog(staff));

                        toggleBtn.setVisible(true);
                        toggleBtn.setManaged(true);
                        toggleBtn.setText(staff.isActive() ? "Deactivate" : "Reactivate");
                        toggleBtn.getStyleClass().setAll(staff.isActive() ? "btn-secondary" : "btn-primary");
                        toggleBtn.setOnAction(e -> handleToggleActive(staff));
                    } else {
                        editBtn.setVisible(false);
                        editBtn.setManaged(false);
                        toggleBtn.setVisible(false);
                        toggleBtn.setManaged(false);
                    }

                    setGraphic(btnBox);
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // Add / Edit Dialog Actions (FR-STF-01, FR-STF-02)
    // ---------------------------------------------------------------

    @FXML
    public void handleOpenAddDialog() {
        openStaffDialog(null);
    }

    private void handleOpenEditDialog(Staff staff) {
        openStaffDialog(staff);
    }

    private void openStaffDialog(Staff existingStaff) {
        try {
            URL fxmlUrl = getClass().getResource("/com/fms/view/staff_dialog.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Label dialogTitle = (Label) root.lookup("#dialogTitle");
            ComboBox<User> userAccountCombo = (ComboBox<User>) root.lookup("#userAccountCombo");
            Label editingUserLabel = (Label) root.lookup("#editingUserLabel");
            ComboBox<StaffSpecialization> specializationCombo = (ComboBox<StaffSpecialization>) root.lookup("#specializationCombo");
            TextField phoneField = (TextField) root.lookup("#phoneField");
            Label activeLabel = (Label) root.lookup("#activeLabel");
            CheckBox activeCheckBox = (CheckBox) root.lookup("#activeCheckBox");
            Label dialogErrorLabel = (Label) root.lookup("#dialogErrorLabel");
            Button cancelButton = (Button) root.lookup("#cancelButton");
            Button saveButton = (Button) root.lookup("#saveButton");

            // Populate specialization dropdown
            specializationCombo.getItems().setAll(StaffSpecialization.values());

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            if (existingStaff == null) {
                // Add mode
                dialogTitle.setText("Add Staff Record");
                editingUserLabel.setVisible(false);
                editingUserLabel.setManaged(false);
                userAccountCombo.setVisible(true);
                userAccountCombo.setManaged(true);

                activeLabel.setVisible(false);
                activeLabel.setManaged(false);
                activeCheckBox.setVisible(false);
                activeCheckBox.setManaged(false);

                // Populate unassigned staff users
                List<User> unassigned = staffService.getAvailableUsersForStaff();
                userAccountCombo.setItems(FXCollections.observableArrayList(unassigned));
                userAccountCombo.setCellFactory(lv -> new ListCell<>() {
                    @Override
                    protected void updateItem(User item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.getFullName() + " (" + item.getUsername() + ")");
                    }
                });
                userAccountCombo.setButtonCell(new ListCell<>() {
                    @Override
                    protected void updateItem(User item, boolean empty) {
                        super.updateItem(item, empty);
                        setText(empty || item == null ? null : item.getFullName() + " (" + item.getUsername() + ")");
                    }
                });

                if (unassigned.isEmpty()) {
                    dialogErrorLabel.setText("No unassigned staff user accounts found. Create a user with Staff role in User Management first.");
                    dialogErrorLabel.setVisible(true);
                    dialogErrorLabel.setManaged(true);
                    saveButton.setDisable(true);
                }
            } else {
                // Edit mode
                dialogTitle.setText("Edit Staff Record - " + existingStaff.getFullName());
                userAccountCombo.setVisible(false);
                userAccountCombo.setManaged(false);
                editingUserLabel.setText(existingStaff.getFullName() + " (" + (existingStaff.getEmail() != null ? existingStaff.getEmail() : "") + ")");
                editingUserLabel.setVisible(true);
                editingUserLabel.setManaged(true);

                specializationCombo.setValue(existingStaff.getSpecialization());
                phoneField.setText(existingStaff.getPhone());

                activeLabel.setVisible(true);
                activeLabel.setManaged(true);
                activeCheckBox.setVisible(true);
                activeCheckBox.setManaged(true);
                activeCheckBox.setSelected(existingStaff.isActive());
            }

            cancelButton.setOnAction(e -> dialogStage.close());

            saveButton.setOnAction(e -> {
                dialogErrorLabel.setVisible(false);
                dialogErrorLabel.setManaged(false);

                StaffSpecialization chosenSpec = specializationCombo.getValue();
                String phone = phoneField.getText();

                try {
                    if (existingStaff == null) {
                        User selectedUser = userAccountCombo.getValue();
                        if (selectedUser == null) {
                            throw new InvalidInputException("userId", "Please select a user account to link.");
                        }
                        staffService.addStaff(selectedUser.getUserId(), chosenSpec, phone);
                    } else {
                        boolean isActive = activeCheckBox.isSelected();
                        staffService.updateStaff(existingStaff.getStaffId(), chosenSpec, phone, isActive);
                    }

                    dialogStage.close();
                    applyFilters();
                } catch (InvalidInputException ex) {
                    dialogErrorLabel.setText(ex.getMessage());
                    dialogErrorLabel.setVisible(true);
                    dialogErrorLabel.setManaged(true);
                } catch (Exception ex) {
                    dialogErrorLabel.setText("Unexpected error: " + ex.getMessage());
                    dialogErrorLabel.setVisible(true);
                    dialogErrorLabel.setManaged(true);
                }
            });

            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/com/fms/css/slotwise-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            dialogStage.setScene(scene);
            dialogStage.setTitle(existingStaff == null ? "Add Staff Record" : "Edit Staff Record");
            dialogStage.setResizable(false);
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Failed to load staff_dialog.fxml: {}", e.getMessage(), e);
        }
    }

    private void handleToggleActive(Staff staff) {
        if (staff.isActive()) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Deactivate Staff Member");
            confirm.setHeaderText("Deactivate " + staff.getFullName() + "?");
            confirm.setContentText("The staff member will be marked inactive and cannot be assigned new tickets.");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                staffService.deactivateStaff(staff.getStaffId());
                applyFilters();
            }
        } else {
            staffService.reactivateStaff(staff.getStaffId());
            applyFilters();
        }
    }

    // ---------------------------------------------------------------
    // Workload & Open Tasks Inspector (FR-STF-03)
    // ---------------------------------------------------------------

    private void handleOpenTasksDialog(Staff staff) {
        try {
            URL fxmlUrl = getClass().getResource("/com/fms/view/staff_tasks_dialog.fxml");
            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Label taskDialogTitle = (Label) root.lookup("#taskDialogTitle");
            Label taskDialogSubtitle = (Label) root.lookup("#taskDialogSubtitle");
            TableView<StaffTaskSummary> tasksTable = (TableView<StaffTaskSummary>) root.lookup("#tasksTable");
            Button closeButton = (Button) root.lookup("#closeButton");

            taskDialogTitle.setText("Active Tasks: " + staff.getFullName());
            String specName = staff.getSpecialization() != null ? staff.getSpecialization().getDisplayName() : "General";
            taskDialogSubtitle.setText(specName + "  |  Contact: " + staff.getPhone());

            // Configure tasksTable columns
            TableColumn<StaffTaskSummary, ?> cId = tasksTable.getColumns().get(0);
            TableColumn<StaffTaskSummary, ?> cFac = tasksTable.getColumns().get(1);
            TableColumn<StaffTaskSummary, ?> cEq = tasksTable.getColumns().get(2);
            TableColumn<StaffTaskSummary, ?> cDesc = tasksTable.getColumns().get(3);
            TableColumn<StaffTaskSummary, ?> cPri = tasksTable.getColumns().get(4);
            TableColumn<StaffTaskSummary, ?> cStat = tasksTable.getColumns().get(5);

            ((TableColumn<StaffTaskSummary, Integer>) cId).setCellValueFactory(new PropertyValueFactory<>("requestId"));
            ((TableColumn<StaffTaskSummary, String>) cFac).setCellValueFactory(new PropertyValueFactory<>("facilityName"));
            ((TableColumn<StaffTaskSummary, String>) cEq).setCellValueFactory(cellData -> {
                String eqName = cellData.getValue().getEquipmentName();
                return new SimpleStringProperty(eqName != null ? eqName : "Facility-wide");
            });
            ((TableColumn<StaffTaskSummary, String>) cDesc).setCellValueFactory(new PropertyValueFactory<>("description"));
            ((TableColumn<StaffTaskSummary, String>) cPri).setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getPriority() != null ? cellData.getValue().getPriority().name() : ""));
            ((TableColumn<StaffTaskSummary, String>) cStat).setCellValueFactory(cellData ->
                    new SimpleStringProperty(cellData.getValue().getStatus() != null ? cellData.getValue().getStatus().name() : ""));

            List<StaffTaskSummary> tasks = staffService.getStaffOpenTasks(staff.getStaffId());
            tasksTable.setItems(FXCollections.observableArrayList(tasks));

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            closeButton.setOnAction(e -> dialogStage.close());

            Scene scene = new Scene(root);
            URL cssUrl = getClass().getResource("/com/fms/css/slotwise-theme.css");
            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
            }
            dialogStage.setScene(scene);
            dialogStage.setTitle("Workload & Tasks - " + staff.getFullName());
            dialogStage.setResizable(false);
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Failed to load staff_tasks_dialog.fxml: {}", e.getMessage(), e);
        }
    }
}
