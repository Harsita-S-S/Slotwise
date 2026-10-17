package com.fms.controller;

import com.fms.dao.FacilityDAO;
import com.fms.exception.BookingConflictException;
import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import com.fms.service.BookingService;
import com.fms.service.SessionManager;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Controller for Module 5: Availability Search & Room Booking.
 * Provides a clean, modern facility directory with direct booking,
 * multi-day and full-day reservation dialog, user self-service, and admin approvals.
 */
public class BookingController {

    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    // Top Header & View Tabs
    @FXML private Button btnTabAvailability;
    @FXML private Button btnTabMyBookings;
    @FXML private Button btnTabAdminApprovals;
    @FXML private HBox alertBanner;
    @FXML private Label alertMessageLabel;

    // View 1: Facility Directory & Booking
    @FXML private VBox availabilityPane;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private TextField minCapacityField;
    @FXML private Button btnNewBooking;

    @FXML private TableView<Facility> facilityTable;
    @FXML private TableColumn<Facility, String> colFacilityName;
    @FXML private TableColumn<Facility, String> colFacilityType;
    @FXML private TableColumn<Facility, String> colFacilityLocation;
    @FXML private TableColumn<Facility, Integer> colFacilityCapacity;
    @FXML private TableColumn<Facility, String> colFacilityAmenities;
    @FXML private TableColumn<Facility, Void> colFacilityPolicy;
    @FXML private TableColumn<Facility, Void> colFacilityAction;

    // View 2: My Bookings
    @FXML private VBox myBookingsPane;
    @FXML private TableView<Booking> myBookingsTable;
    @FXML private TableColumn<Booking, String> colMyFacility;
    @FXML private TableColumn<Booking, String> colMyBuilding;
    @FXML private TableColumn<Booking, String> colMyDate;
    @FXML private TableColumn<Booking, String> colMySlot;
    @FXML private TableColumn<Booking, String> colMyAttendees;
    @FXML private TableColumn<Booking, String> colMyPurpose;
    @FXML private TableColumn<Booking, String> colMyStatus;
    @FXML private TableColumn<Booking, Void> colMyActions;

    // View 3: Admin Approvals
    @FXML private VBox adminApprovalsPane;
    @FXML private DatePicker adminDatePicker;
    @FXML private ComboBox<String> adminStatusFilterCombo;
    @FXML private TableView<Booking> adminBookingsTable;
    @FXML private TableColumn<Booking, String> colAdminId;
    @FXML private TableColumn<Booking, String> colAdminUser;
    @FXML private TableColumn<Booking, String> colAdminFacility;
    @FXML private TableColumn<Booking, String> colAdminDate;
    @FXML private TableColumn<Booking, String> colAdminSlot;
    @FXML private TableColumn<Booking, String> colAdminAttendees;
    @FXML private TableColumn<Booking, String> colAdminPurpose;
    @FXML private TableColumn<Booking, String> colAdminStatus;
    @FXML private TableColumn<Booking, Void> colAdminActions;

    private final BookingService bookingService = new BookingService();
    private final FacilityDAO facilityDAO = new FacilityDAO();

    private final ObservableList<Facility> masterFacilityList = FXCollections.observableArrayList();
    private FilteredList<Facility> filteredFacilityList;

    @FXML
    public void initialize() {
        setupViewTabs();
        setupFacilityTable();
        setupFilters();
        setupMyBookingsTable();
        setupAdminTable();

        loadFacilities();
        handleSwitchToAvailability();
    }

    private void setupViewTabs() {
        boolean isAdmin = SessionManager.getInstance().isAdmin();
        if (btnTabAdminApprovals != null) {
            btnTabAdminApprovals.setVisible(isAdmin);
            btnTabAdminApprovals.setManaged(isAdmin);
        }
    }

    // ---------------------------------------------------------------
    // View Switching
    // ---------------------------------------------------------------

    @FXML
    public void handleSwitchToAvailability() {
        setTabActive(btnTabAvailability);
        setPaneVisible(availabilityPane, true);
        setPaneVisible(myBookingsPane, false);
        setPaneVisible(adminApprovalsPane, false);
        loadFacilities();
    }

    @FXML
    public void handleSwitchToMyBookings() {
        setTabActive(btnTabMyBookings);
        setPaneVisible(availabilityPane, false);
        setPaneVisible(myBookingsPane, true);
        setPaneVisible(adminApprovalsPane, false);
        loadMyBookings();
    }

    @FXML
    public void handleSwitchToAdminApprovals() {
        setTabActive(btnTabAdminApprovals);
        setPaneVisible(availabilityPane, false);
        setPaneVisible(myBookingsPane, false);
        setPaneVisible(adminApprovalsPane, true);
        loadAdminBookings();
    }

    private void setTabActive(Button activeBtn) {
        if (btnTabAvailability != null) btnTabAvailability.getStyleClass().setAll("view-toggle-btn");
        if (btnTabMyBookings != null) btnTabMyBookings.getStyleClass().setAll("view-toggle-btn");
        if (btnTabAdminApprovals != null) btnTabAdminApprovals.getStyleClass().setAll("view-toggle-btn");

        if (activeBtn != null) {
            activeBtn.getStyleClass().setAll("view-toggle-btn-active");
        }
    }

    private void setPaneVisible(VBox pane, boolean visible) {
        if (pane != null) {
            pane.setVisible(visible);
            pane.setManaged(visible);
        }
    }

    // ---------------------------------------------------------------
    // View 1: Facility Directory & Search
    // ---------------------------------------------------------------

    private void setupFacilityTable() {
        filteredFacilityList = new FilteredList<>(masterFacilityList, p -> true);
        facilityTable.setItems(filteredFacilityList);

        colFacilityName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        colFacilityType.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getType() != null ? c.getValue().getType().name() : "Other"));

        colFacilityLocation.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getBuilding() + " • Floor " + c.getValue().getFloor()));

        colFacilityCapacity.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getCapacity()).asObject());
        colFacilityAmenities.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getAmenities() != null && !c.getValue().getAmenities().isBlank()
                        ? c.getValue().getAmenities() : "Standard"));

        // Approval Policy Badge Column
        colFacilityPolicy.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Facility f = getTableRow().getItem();
                    Label badge = new Label();
                    if (f.isRequiresApproval()) {
                        badge.setText("⏳ Needs Approval");
                        badge.setStyle("-fx-background-color: rgba(201, 168, 76, 0.18); -fx-text-fill: #94711E; -fx-padding: 3 8; -fx-background-radius: 6; -fx-font-size: 11px; -fx-font-weight: bold;");
                    } else {
                        badge.setText("✓ Instant");
                        badge.setStyle("-fx-background-color: rgba(107, 143, 113, 0.18); -fx-text-fill: #3D6844; -fx-padding: 3 8; -fx-background-radius: 6; -fx-font-size: 11px; -fx-font-weight: bold;");
                    }
                    setGraphic(badge);
                }
            }
        });

        // Direct "Book Room" Action Button Column
        colFacilityAction.setCellFactory(col -> new TableCell<>() {
            private final Button bookBtn = new Button("📅 Book");
            {
                bookBtn.getStyleClass().add("btn-primary");
                bookBtn.setStyle("-fx-font-size: 11px; -fx-padding: 5 12;");
                bookBtn.setOnAction(e -> {
                    Facility f = getTableRow().getItem();
                    if (f != null) {
                        openBookingDialog(f);
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    setGraphic(bookBtn);
                }
            }
        });
    }

    private void setupFilters() {
        if (typeFilterCombo != null) {
            List<String> types = new ArrayList<>();
            types.add("All Types");
            for (FacilityType ft : FacilityType.values()) {
                types.add(ft.name());
            }
            typeFilterCombo.setItems(FXCollections.observableArrayList(types));
            typeFilterCombo.setValue("All Types");
            typeFilterCombo.valueProperty().addListener((obs, oldV, newV) -> applyFilters());
        }

        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldV, newV) -> applyFilters());
        }

        if (minCapacityField != null) {
            minCapacityField.textProperty().addListener((obs, oldV, newV) -> applyFilters());
        }
    }

    private void applyFilters() {
        String query = searchField != null && searchField.getText() != null
                ? searchField.getText().trim().toLowerCase() : "";
        String selectedType = typeFilterCombo != null ? typeFilterCombo.getValue() : "All Types";
        String capText = minCapacityField != null && minCapacityField.getText() != null
                ? minCapacityField.getText().trim() : "";

        int minCap = 0;
        if (!capText.isEmpty()) {
            try { minCap = Integer.parseInt(capText); } catch (NumberFormatException ignored) {}
        }
        final int filterMinCap = minCap;

        filteredFacilityList.setPredicate(facility -> {
            if (facility == null) return false;

            // Search query matches name, building, or amenities
            if (!query.isEmpty()) {
                boolean matchesName = facility.getName() != null && facility.getName().toLowerCase().contains(query);
                boolean matchesBldg = facility.getBuilding() != null && facility.getBuilding().toLowerCase().contains(query);
                boolean matchesAmen = facility.getAmenities() != null && facility.getAmenities().toLowerCase().contains(query);
                if (!matchesName && !matchesBldg && !matchesAmen) return false;
            }

            // Type filter
            if (selectedType != null && !selectedType.equals("All Types")) {
                if (facility.getType() == null || !facility.getType().name().equalsIgnoreCase(selectedType)) {
                    return false;
                }
            }

            // Min capacity filter
            if (filterMinCap > 0 && facility.getCapacity() < filterMinCap) {
                return false;
            }

            return true;
        });
    }

    @FXML
    private void handleResetFilters() {
        if (searchField != null) searchField.clear();
        if (typeFilterCombo != null) typeFilterCombo.setValue("All Types");
        if (minCapacityField != null) minCapacityField.clear();
        applyFilters();
    }

    private void loadFacilities() {
        try {
            List<Facility> active = facilityDAO.findAllActive();
            masterFacilityList.setAll(active);
            applyFilters();
        } catch (Exception e) {
            logger.error("Failed to load facilities for booking: {}", e.getMessage(), e);
            showAlert("Failed to load facilities: " + e.getMessage());
        }
    }

    @FXML
    public void handleOpenNewBookingDialog() {
        openBookingDialog(null);
    }

    // ---------------------------------------------------------------
    // Booking Dialog Workflow (Single-Day & Multi-Day Support)
    // ---------------------------------------------------------------

    public void openBookingDialog(Facility preselectedFacility) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fms/view/booking_dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Reserve Facility — SlotWise");

            // Look up dialog elements with dual fallback (FXMLLoader namespace + CSS ID)
            Map<String, Object> ns = loader.getNamespace();
            ComboBox<Facility> dlgFacilityCombo = findDialogNode(ns, root, "dlgFacilityCombo");
            RadioButton rbSingleDay = findDialogNode(ns, root, "rbSingleDay");
            RadioButton rbMultiDay = findDialogNode(ns, root, "rbMultiDay");
            HBox singleDateBox = findDialogNode(ns, root, "singleDateBox");
            HBox multiDateBox = findDialogNode(ns, root, "multiDateBox");
            DatePicker dlgDatePicker = findDialogNode(ns, root, "dlgDatePicker");
            DatePicker dlgStartDatePicker = findDialogNode(ns, root, "dlgStartDatePicker");
            DatePicker dlgEndDatePicker = findDialogNode(ns, root, "dlgEndDatePicker");

            CheckBox chkFullDay = findDialogNode(ns, root, "chkFullDay");
            HBox timeRangeBox = findDialogNode(ns, root, "timeRangeBox");
            ComboBox<String> comboStartTime = findDialogNode(ns, root, "comboStartTime");
            ComboBox<String> comboEndTime = findDialogNode(ns, root, "comboEndTime");
            Label lblSlotSummary = findDialogNode(ns, root, "lblSlotSummary");

            TextField dlgPurposeField = findDialogNode(ns, root, "dlgPurposeField");
            TextField dlgAttendeesField = findDialogNode(ns, root, "dlgAttendeesField");
            Label dlgCapacityFeedback = findDialogNode(ns, root, "dlgCapacityFeedback");
            HBox dlgApprovalNotice = findDialogNode(ns, root, "dlgApprovalNotice");
            Label dlgApprovalNoticeLabel = findDialogNode(ns, root, "dlgApprovalNoticeLabel");
            Label dialogErrorLabel = findDialogNode(ns, root, "dialogErrorLabel");

            Button btnCancel = findDialogNode(ns, root, "btnCancel");
            Button btnConfirm = findDialogNode(ns, root, "btnConfirm");

            if (dlgFacilityCombo == null) {
                throw new IllegalStateException("Required dialog control 'dlgFacilityCombo' could not be resolved.");
            }

            // Populate facilities
            List<Facility> facilities = facilityDAO.findAllActive();
            dlgFacilityCombo.setItems(FXCollections.observableArrayList(facilities));
            dlgFacilityCombo.setConverter(new StringConverter<>() {
                @Override
                public String toString(Facility f) {
                    if (f == null) return "";
                    return f.getName() + " (" + f.getBuilding() + " • Cap: " + f.getCapacity() + (f.isRequiresApproval() ? " • Appr. Required" : "") + ")";
                }
                @Override
                public Facility fromString(String string) { return null; }
            });

            if (preselectedFacility != null) {
                dlgFacilityCombo.setValue(preselectedFacility);
            } else if (!facilities.isEmpty()) {
                dlgFacilityCombo.setValue(facilities.get(0));
            }

            // Smart date defaults (tomorrow if after 18:00 today)
            LocalDate defaultDate = LocalTime.now().isAfter(LocalTime.of(18, 0))
                    ? LocalDate.now().plusDays(1) : LocalDate.now();

            dlgDatePicker.setValue(defaultDate);
            dlgStartDatePicker.setValue(defaultDate);
            dlgEndDatePicker.setValue(defaultDate.plusDays(1));

            // Single vs Multi-Day toggle
            rbSingleDay.selectedProperty().addListener((obs, oldV, isSingle) -> {
                singleDateBox.setVisible(isSingle);
                singleDateBox.setManaged(isSingle);
                multiDateBox.setVisible(!isSingle);
                multiDateBox.setManaged(!isSingle);
            });

            // Populate time combos (Slots 1 to 10: 08:00 to 18:00)
            List<String> startTimes = List.of("08:00", "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00");
            List<String> endTimes   = List.of("09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00");
            comboStartTime.setItems(FXCollections.observableArrayList(startTimes));
            comboEndTime.setItems(FXCollections.observableArrayList(endTimes));
            comboStartTime.setValue("09:00");
            comboEndTime.setValue("10:00");

            // Helper to update slot summary label
            Runnable updateSlotSummary = () -> {
                if (chkFullDay.isSelected()) {
                    timeRangeBox.setDisable(true);
                    lblSlotSummary.setText("10 slots (Full Day: 08:00 – 18:00)");
                } else {
                    timeRangeBox.setDisable(false);
                    int startIdx = startTimes.indexOf(comboStartTime.getValue());
                    int endIdx = endTimes.indexOf(comboEndTime.getValue());
                    if (startIdx >= 0 && endIdx >= 0 && endIdx >= startIdx) {
                        int count = endIdx - startIdx + 1;
                        lblSlotSummary.setText(count + " slot(s) (" + count + " hr" + (count > 1 ? "s" : "") + ")");
                        lblSlotSummary.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #D97757;");
                    } else {
                        lblSlotSummary.setText("Invalid range (end before start)");
                        lblSlotSummary.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #A13D3D;");
                    }
                }
            };

            chkFullDay.selectedProperty().addListener((obs, oldV, newV) -> updateSlotSummary.run());
            comboStartTime.valueProperty().addListener((obs, oldV, newV) -> updateSlotSummary.run());
            comboEndTime.valueProperty().addListener((obs, oldV, newV) -> updateSlotSummary.run());
            updateSlotSummary.run();

            // Live capacity feedback & approval notice update
            Runnable updateFacilityFeedback = () -> {
                Facility sel = dlgFacilityCombo.getValue();
                if (sel != null) {
                    if (sel.isRequiresApproval()) {
                        dlgApprovalNotice.setVisible(true);
                        dlgApprovalNotice.setManaged(true);
                        dlgApprovalNoticeLabel.setText("ℹ '" + sel.getName() + "' requires Admin approval. Your booking will start as 'Pending'.");
                    } else {
                        dlgApprovalNotice.setVisible(false);
                        dlgApprovalNotice.setManaged(false);
                    }

                    String attStr = dlgAttendeesField.getText();
                    if (attStr != null && !attStr.isBlank()) {
                        try {
                            int att = Integer.parseInt(attStr.trim());
                            if (att > sel.getCapacity()) {
                                dlgCapacityFeedback.setText("⚠ Warning: " + att + " attendees exceeds room capacity of " + sel.getCapacity() + "!");
                                dlgCapacityFeedback.setStyle("-fx-text-fill: #A13D3D; -fx-font-weight: bold; -fx-font-size: 11px;");
                            } else {
                                dlgCapacityFeedback.setText("✓ " + att + " attendees (fits inside capacity: " + sel.getCapacity() + ")");
                                dlgCapacityFeedback.setStyle("-fx-text-fill: #3D6844; -fx-font-weight: bold; -fx-font-size: 11px;");
                            }
                            dlgCapacityFeedback.setVisible(true);
                            dlgCapacityFeedback.setManaged(true);
                        } catch (NumberFormatException ignored) {
                            dlgCapacityFeedback.setVisible(false);
                            dlgCapacityFeedback.setManaged(false);
                        }
                    } else {
                        dlgCapacityFeedback.setVisible(false);
                        dlgCapacityFeedback.setManaged(false);
                    }
                }
            };

            dlgFacilityCombo.valueProperty().addListener((obs, oldV, newV) -> updateFacilityFeedback.run());
            dlgAttendeesField.textProperty().addListener((obs, oldV, newV) -> updateFacilityFeedback.run());
            updateFacilityFeedback.run();

            // Cancel button
            btnCancel.setOnAction(e -> dialogStage.close());

            // Confirm button
            btnConfirm.setOnAction(e -> {
                dialogErrorLabel.setVisible(false);
                dialogErrorLabel.setManaged(false);

                Facility fac = dlgFacilityCombo.getValue();
                if (fac == null) {
                    showDialogError(dialogErrorLabel, "Please select a facility.");
                    return;
                }

                String purpose = dlgPurposeField.getText();
                if (purpose == null || purpose.trim().isEmpty()) {
                    showDialogError(dialogErrorLabel, "Please enter a booking purpose.");
                    return;
                }

                String attStr = dlgAttendeesField.getText();
                int attendees;
                try {
                    attendees = Integer.parseInt(attStr != null ? attStr.trim() : "");
                    if (attendees <= 0) {
                        showDialogError(dialogErrorLabel, "Attendees must be greater than zero.");
                        return;
                    }
                } catch (Exception ex) {
                    showDialogError(dialogErrorLabel, "Attendees count must be a valid number.");
                    return;
                }

                // Compute slots
                List<Integer> slots = new ArrayList<>();
                if (chkFullDay.isSelected()) {
                    for (int s = 1; s <= 10; s++) slots.add(s);
                } else {
                    int startIdx = startTimes.indexOf(comboStartTime.getValue());
                    int endIdx = endTimes.indexOf(comboEndTime.getValue());
                    if (startIdx < 0 || endIdx < 0 || endIdx < startIdx) {
                        showDialogError(dialogErrorLabel, "End time cannot be earlier than start time.");
                        return;
                    }
                    for (int s = (startIdx + 1); s <= (endIdx + 1); s++) {
                        slots.add(s);
                    }
                }

                try {
                    List<Booking> created;
                    if (rbSingleDay.isSelected()) {
                        LocalDate bookingDate = dlgDatePicker.getValue();
                        if (bookingDate == null) {
                            showDialogError(dialogErrorLabel, "Please select a booking date.");
                            return;
                        }
                        // Use requestExtendedApproval=true so full-day / multi-slot bookings route cleanly
                        created = bookingService.createBooking(
                                fac.getFacilityId(), bookingDate, slots, purpose, attendees, true, false);
                    } else {
                        LocalDate startDate = dlgStartDatePicker.getValue();
                        LocalDate endDate = dlgEndDatePicker.getValue();
                        if (startDate == null || endDate == null) {
                            showDialogError(dialogErrorLabel, "Please specify both start date and end date.");
                            return;
                        }
                        created = bookingService.createMultiDayBooking(
                                fac.getFacilityId(), startDate, endDate, slots, purpose, attendees);
                    }

                    dialogStage.close();
                    String statusText = (created != null && !created.isEmpty() && created.get(0).getStatus() == BookingStatus.Pending)
                            ? "Pending Admin Approval" : "Confirmed & Approved";

                    // Instant Reactive Table Refresh
                    loadMyBookings();
                    loadFacilities();
                    if (SessionManager.getInstance().isAdmin()) {
                        loadAdminBookings();
                    }

                    // Success Popup Confirmation
                    Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                    successAlert.setTitle("Booking Success");
                    successAlert.setHeaderText("Reservation Successfully Placed!");
                    successAlert.setContentText(String.format(
                            "Facility: %s (%s)\n" +
                            "Slots: %d slot(s)\n" +
                            "Attendees: %d\n" +
                            "Purpose: %s\n" +
                            "Status: %s",
                            fac.getName(), fac.getBuilding(),
                            created != null ? created.size() : 0,
                            attendees, purpose, statusText
                    ));
                    successAlert.showAndWait();

                } catch (BookingConflictException conflictEx) {
                    showDialogError(dialogErrorLabel, conflictEx.getMessage());
                } catch (InvalidInputException inputEx) {
                    showDialogError(dialogErrorLabel, inputEx.getMessage());
                } catch (Exception ex) {
                    logger.error("Booking error: {}", ex.getMessage(), ex);
                    showDialogError(dialogErrorLabel, "Booking error: " + ex.getMessage());
                }
            });

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/com/fms/css/slotwise-theme.css").toExternalForm());
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

        } catch (Exception e) {
            logger.error("Failed to open booking dialog: {}", e.getMessage(), e);
            showAlert("Could not open booking dialog: " + e.getMessage());
        }
    }

    private void showDialogError(Label label, String msg) {
        if (label != null) {
            label.setText(msg);
            label.setVisible(true);
            label.setManaged(true);
        }
    }

    @SuppressWarnings("unchecked")
    private <T extends Node> T findDialogNode(Map<String, Object> ns, Parent root, String id) {
        if (ns != null && ns.containsKey(id) && ns.get(id) instanceof Node) {
            return (T) ns.get(id);
        }
        return (T) root.lookup("#" + id);
    }

    // ---------------------------------------------------------------
    // View 2: My Bookings
    // ---------------------------------------------------------------

    private void setupMyBookingsTable() {
        colMyFacility.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFacilityName()));
        colMyBuilding.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFacilityBuilding()));
        colMyDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBookingDate().toString()));
        colMySlot.setCellValueFactory(c -> new SimpleStringProperty("Slot " + c.getValue().getSlotNo() + " (" + c.getValue().getSlotTime() + ")"));
        colMyAttendees.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getAttendees())));
        colMyPurpose.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPurpose()));

        colMyStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Booking b = getTableRow().getItem();
                    Label badge = new Label(b.getStatus().name());
                    badge.getStyleClass().add(getStatusBadgeClass(b.getStatus()));
                    setGraphic(badge);
                }
            }
        });

        colMyActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Booking b = getTableRow().getItem();
                    HBox box = new HBox(8);
                    box.setAlignment(Pos.CENTER);

                    boolean canCancel = (b.getStatus() == BookingStatus.Approved || b.getStatus() == BookingStatus.Pending)
                            && (b.getBookingDate().isAfter(LocalDate.now()) ||
                               (b.getBookingDate().isEqual(LocalDate.now()) && b.getSlotNo() > BookingService.getCurrentSlotNumber()));

                    if (canCancel) {
                        Button cancelBtn = new Button("Cancel");
                        cancelBtn.getStyleClass().add("btn-secondary");
                        cancelBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
                        cancelBtn.setOnAction(e -> handleCancelUserBooking(b));
                        box.getChildren().add(cancelBtn);
                    } else if (b.getStatus() == BookingStatus.Rejected && b.getRejectionReason() != null) {
                        Label reasonLbl = new Label("Reason: " + b.getRejectionReason());
                        reasonLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #A15252;");
                        box.getChildren().add(reasonLbl);
                    }

                    setGraphic(box);
                }
            }
        });
    }

    @FXML
    public void loadMyBookings() {
        try {
            List<Booking> bookings = bookingService.getMyBookings();
            myBookingsTable.setItems(FXCollections.observableArrayList(bookings));
        } catch (Exception e) {
            logger.error("Error loading user bookings: {}", e.getMessage(), e);
        }
    }

    private void handleCancelUserBooking(Booking b) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Cancel booking for " + b.getFacilityName() + " on " + b.getBookingDate() + " (Slot " + b.getSlotNo() + ")?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Cancellation");
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                try {
                    bookingService.cancelBooking(b.getBookingId());
                    loadMyBookings();
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Booking Cancelled");
                    info.setHeaderText("Reservation Cancelled Successfully");
                    info.setContentText("Your booking for " + b.getFacilityName() + " on " + b.getBookingDate() + " has been cancelled.");
                    info.showAndWait();
                } catch (Exception ex) {
                    showAlert("Error cancelling booking: " + ex.getMessage());
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // View 3: Admin Approvals Console
    // ---------------------------------------------------------------

    private void setupAdminTable() {
        colAdminId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getBookingId())));
        colAdminUser.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getUserFullName() != null ? c.getValue().getUserFullName() : c.getValue().getUsername()));
        colAdminFacility.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFacilityName()));
        colAdminDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBookingDate().toString()));
        colAdminSlot.setCellValueFactory(c -> new SimpleStringProperty("Slot " + c.getValue().getSlotNo()));
        colAdminAttendees.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getAttendees())));
        colAdminPurpose.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPurpose()));

        colAdminStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Booking b = getTableRow().getItem();
                    Label badge = new Label(b.getStatus().name());
                    badge.getStyleClass().add(getStatusBadgeClass(b.getStatus()));
                    setGraphic(badge);
                }
            }
        });

        colAdminActions.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                } else {
                    Booking b = getTableRow().getItem();
                    HBox box = new HBox(6);
                    box.setAlignment(Pos.CENTER);

                    if (b.getStatus() == BookingStatus.Pending) {
                        Button approveBtn = new Button("✓ Approve");
                        approveBtn.setStyle("-fx-background-color: #6B8F71; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8;");
                        approveBtn.setOnAction(e -> handleApproveBooking(b));

                        Button rejectBtn = new Button("✕ Reject");
                        rejectBtn.setStyle("-fx-background-color: #D9534F; -fx-text-fill: white; -fx-font-size: 11px; -fx-padding: 4 8;");
                        rejectBtn.setOnAction(e -> handleRejectBooking(b));

                        box.getChildren().addAll(approveBtn, rejectBtn);
                    } else {
                        Label doneLbl = new Label("Processed");
                        doneLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A6B66;");
                        box.getChildren().add(doneLbl);
                    }

                    setGraphic(box);
                }
            }
        });

        if (adminStatusFilterCombo != null) {
            adminStatusFilterCombo.setItems(FXCollections.observableArrayList("Pending", "All Statuses", "Approved", "Rejected"));
            adminStatusFilterCombo.setValue("Pending");
            adminStatusFilterCombo.valueProperty().addListener((obs, oldV, newV) -> loadAdminBookings());
        }

        if (adminDatePicker != null) {
            adminDatePicker.valueProperty().addListener((obs, oldV, newV) -> loadAdminBookings());
        }
    }

    @FXML
    public void loadAdminBookings() {
        try {
            LocalDate date = adminDatePicker != null ? adminDatePicker.getValue() : null;
            String statusStr = adminStatusFilterCombo != null ? adminStatusFilterCombo.getValue() : "Pending";
            BookingStatus status = "All Statuses".equals(statusStr) ? null : BookingStatus.valueOf(statusStr);

            List<Booking> bookings = bookingService.searchAllBookings(date, null, null, status);
            adminBookingsTable.setItems(FXCollections.observableArrayList(bookings));
        } catch (Exception e) {
            logger.error("Error loading admin bookings: {}", e.getMessage(), e);
        }
    }

    private void handleApproveBooking(Booking b) {
        try {
            bookingService.approveBooking(b.getBookingId());
            loadAdminBookings();
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Booking Approved");
            alert.setHeaderText("Reservation Approved");
            alert.setContentText("Booking #" + b.getBookingId() + " for " + b.getFacilityName() + " has been approved.");
            alert.showAndWait();
        } catch (Exception e) {
            showAlert("Approval failed: " + e.getMessage());
        }
    }

    private void handleRejectBooking(Booking b) {
        TextInputDialog dialog = new TextInputDialog("Scheduling conflict / room reassignment");
        dialog.setTitle("Reject Booking");
        dialog.setHeaderText("Provide rejection reason for Booking #" + b.getBookingId());
        dialog.setContentText("Reason:");

        dialog.showAndWait().ifPresent(reason -> {
            if (reason.trim().isEmpty()) {
                showAlert("Rejection reason cannot be empty.");
                return;
            }
            try {
                bookingService.rejectBooking(b.getBookingId(), reason.trim());
                loadAdminBookings();
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Booking Rejected");
                alert.setHeaderText("Reservation Rejected");
                alert.setContentText("Booking #" + b.getBookingId() + " was rejected.\nReason: " + reason.trim());
                alert.showAndWait();
            } catch (Exception e) {
                showAlert("Rejection failed: " + e.getMessage());
            }
        });
    }

    private String getStatusBadgeClass(BookingStatus status) {
        if (status == null) return "badge-status-pending";
        return switch (status) {
            case Approved -> "badge-status-approved";
            case Pending -> "badge-status-pending";
            case Rejected, Cancelled -> "badge-status-rejected";
            case Completed -> "badge-status-completed";
        };
    }

    // ---------------------------------------------------------------
    // Feedback Helpers & Toast Notifications
    // ---------------------------------------------------------------

    private void showAlert(String msg) {
        alertMessageLabel.setText(msg);
        alertBanner.setVisible(true);
        alertBanner.setManaged(true);
    }

    @FXML
    private void handleDismissAlert() {
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);
    }

    private void showSuccessToast(String message) {
        try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}

        Label toast = new Label(message);
        toast.getStyleClass().add("toast-success");
        toast.setMaxWidth(500);
        toast.setTranslateY(-60);
        toast.setOpacity(0);

        StackPane overlay = new StackPane(toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(16, 0, 0, 0));
        overlay.setPickOnBounds(false);
        overlay.setMouseTransparent(true);

        if (alertBanner == null || alertBanner.getScene() == null) {
            showAlert(message);
            return;
        }
        Parent sceneRoot = alertBanner.getScene().getRoot();
        if (!(sceneRoot instanceof Pane rootPane)) {
            showAlert(message);
            return;
        }

        rootPane.getChildren().add(overlay);
        overlay.prefWidthProperty().bind(rootPane.widthProperty());
        overlay.prefHeightProperty().bind(rootPane.heightProperty());

        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), toast);
        slideIn.setFromY(-60);
        slideIn.setToY(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        javafx.animation.ParallelTransition enter = new javafx.animation.ParallelTransition(slideIn, fadeIn);
        PauseTransition hold = new PauseTransition(Duration.millis(2000));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(350), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        SequentialTransition seq = new SequentialTransition(enter, hold, fadeOut);
        seq.setOnFinished(e -> rootPane.getChildren().remove(overlay));
        seq.play();

        logger.info("Toast shown: {}", message);
    }
}
