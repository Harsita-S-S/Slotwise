package com.fms.controller;

import com.fms.exception.BookingConflictException;
import com.fms.exception.InvalidInputException;
import com.fms.model.*;
import com.fms.service.BookingService;
import com.fms.service.SessionManager;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * Controller for Module 5: Availability Search and Booking.
 * Handles interactive 10-slot visual grid, multi-slot selection (BR-03),
 * modal reservation confirmation, cancellation (BR-05), and Admin approvals (FR-BKG-07).
 */
public class BookingController {
    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);

    // Header tabs
    @FXML private Button btnTabAvailability;
    @FXML private Button btnTabMyBookings;
    @FXML private Button btnTabAdminApprovals;

    // Feedback alert
    @FXML private HBox alertBanner;
    @FXML private Label alertMessageLabel;

    // View 1: Availability
    @FXML private VBox availabilityPane;
    @FXML private DatePicker datePicker;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private TextField minCapacityField;
    @FXML private TextField amenityFilterField;
    @FXML private VBox matrixContainer;

    // Selection floating bar
    @FXML private HBox selectionBar;
    @FXML private Label selectionFacilityLabel;
    @FXML private Label selectionSlotsLabel;
    @FXML private Button btnProceedBooking;

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

    // Active slot selection state (BR-03)
    private Facility selectedFacility = null;
    private final Set<Integer> selectedSlots = new TreeSet<>();
    private final Map<Integer, Button> activeSlotButtons = new HashMap<>();

    @FXML
    public void initialize() {
        setupDatePickers();
        setupFilters();
        setupMyBookingsTable();
        setupAdminTable();

        // Admin tab visibility
        boolean isAdmin = SessionManager.getInstance().isAdmin();
        btnTabAdminApprovals.setVisible(isAdmin);
        btnTabAdminApprovals.setManaged(isAdmin);

        // Load initial grid
        handleSearchSlots();
    }

    private void setupDatePickers() {
        datePicker.setValue(LocalDate.now());

        // Restrict dates to today up to +30 days (BR-02)
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                LocalDate today = LocalDate.now();
                if (item.isBefore(today) || item.isAfter(today.plusDays(30))) {
                    setDisable(true);
                    setStyle("-fx-background-color: #EEE8DF; -fx-text-fill: #A89F91;");
                }
            }
        });
    }

    private void setupFilters() {
        typeFilterCombo.setItems(FXCollections.observableArrayList("All Types", "Classroom", "Laboratory", "Hall"));
        typeFilterCombo.setValue("All Types");

        adminStatusFilterCombo.setItems(FXCollections.observableArrayList("All Statuses", "Pending", "Approved", "Rejected", "Cancelled", "Completed"));
        adminStatusFilterCombo.setValue("All Statuses");
    }

    // ---------------------------------------------------------------
    // Sub-Navigation Tabs
    // ---------------------------------------------------------------

    @FXML
    private void handleSwitchToAvailability() {
        setActiveTabButton(btnTabAvailability);
        availabilityPane.setVisible(true);
        availabilityPane.setManaged(true);
        myBookingsPane.setVisible(false);
        myBookingsPane.setManaged(false);
        adminApprovalsPane.setVisible(false);
        adminApprovalsPane.setManaged(false);
        handleSearchSlots();
    }

    @FXML
    private void handleSwitchToMyBookings() {
        setActiveTabButton(btnTabMyBookings);
        availabilityPane.setVisible(false);
        availabilityPane.setManaged(false);
        myBookingsPane.setVisible(true);
        myBookingsPane.setManaged(true);
        adminApprovalsPane.setVisible(false);
        adminApprovalsPane.setManaged(false);
        handleRefreshMyBookings();
    }

    @FXML
    private void handleSwitchToAdminApprovals() {
        setActiveTabButton(btnTabAdminApprovals);
        availabilityPane.setVisible(false);
        availabilityPane.setManaged(false);
        myBookingsPane.setVisible(false);
        myBookingsPane.setManaged(false);
        adminApprovalsPane.setVisible(true);
        adminApprovalsPane.setManaged(true);
        handleFilterAdminBookings();
    }

    private void setActiveTabButton(Button active) {
        btnTabAvailability.getStyleClass().setAll("view-toggle-btn");
        btnTabMyBookings.getStyleClass().setAll("view-toggle-btn");
        btnTabAdminApprovals.getStyleClass().setAll("view-toggle-btn");
        active.getStyleClass().setAll("view-toggle-btn-active");
    }

    // ---------------------------------------------------------------
    // Visual Slot Matrix (FR-BKG-01, FR-BKG-02)
    // ---------------------------------------------------------------

    @FXML
    private void handleSearchSlots() {
        LocalDate chosenDate = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();

        String typeStr = typeFilterCombo.getValue();
        FacilityType facilityType = null;
        if (typeStr != null && !typeStr.equals("All Types")) {
            facilityType = FacilityType.valueOf(typeStr);
        }

        Integer minCapacity = null;
        if (minCapacityField.getText() != null && !minCapacityField.getText().trim().isEmpty()) {
            try {
                minCapacity = Integer.parseInt(minCapacityField.getText().trim());
            } catch (NumberFormatException ignored) {}
        }

        String amenity = amenityFilterField.getText() != null && !amenityFilterField.getText().trim().isEmpty() ?
                amenityFilterField.getText().trim() : null;

        List<FacilitySlotSummary> summaries = bookingService.getFacilitySlotGrid(chosenDate, facilityType, minCapacity, amenity);
        renderMatrix(summaries, chosenDate);
    }

    private void renderMatrix(List<FacilitySlotSummary> summaries, LocalDate date) {
        matrixContainer.getChildren().clear();
        clearSlotSelection();

        if (summaries.isEmpty()) {
            VBox emptyBox = new VBox(10);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPadding(new javafx.geometry.Insets(40));
            emptyBox.getChildren().addAll(
                new Label("No active facilities found matching your criteria."),
                new Label("Try clearing the amenity or capacity filters.")
            );
            matrixContainer.getChildren().add(emptyBox);
            return;
        }

        // Header timeline row
        HBox timelineHeader = new HBox(8);
        timelineHeader.setAlignment(Pos.CENTER_LEFT);
        timelineHeader.setPadding(new javafx.geometry.Insets(6, 16, 6, 16));

        Label roomHeaderLabel = new Label("FACILITY / ROOM");
        roomHeaderLabel.setPrefWidth(240);
        roomHeaderLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #7A6B66; -fx-font-size: 11px;");
        timelineHeader.getChildren().add(roomHeaderLabel);

        for (int i = 1; i <= 10; i++) {
            Label slotHdr = new Label(Booking.formatSlotTime(i).replace(" - ", "\n"));
            slotHdr.setPrefWidth(72);
            slotHdr.setAlignment(Pos.CENTER);
            slotHdr.setStyle("-fx-font-size: 9px; -fx-text-fill: #7A6B66; -fx-font-weight: bold; -fx-text-alignment: center;");
            timelineHeader.getChildren().add(slotHdr);
        }
        matrixContainer.getChildren().add(timelineHeader);

        // Facility rows
        for (FacilitySlotSummary summary : summaries) {
            Facility facility = summary.getFacility();

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card");
            row.setStyle("-fx-padding: 12 16;");

            // Left room info column
            VBox roomInfo = new VBox(4);
            roomInfo.setPrefWidth(240);

            Label nameLbl = new Label(facility.getName());
            nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #4A3E3D;");

            HBox tags = new HBox(6);
            Label bldgLbl = new Label(facility.getBuilding() + " (Fl " + facility.getFloor() + ")");
            bldgLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #7A6B66;");

            Label capLbl = new Label("Cap: " + facility.getCapacity());
            capLbl.getStyleClass().add("amenity-tag");

            tags.getChildren().addAll(bldgLbl, capLbl);

            if (facility.isRequiresApproval()) {
                Label appLbl = new Label("Approval Req.");
                appLbl.getStyleClass().add("badge-approval");
                tags.getChildren().add(appLbl);
            }

            roomInfo.getChildren().addAll(nameLbl, tags);
            row.getChildren().add(roomInfo);

            // 10 Slot buttons
            for (int slot = 1; slot <= 10; slot++) {
                SlotStatus status = summary.getStatusForSlot(slot);
                Button slotBtn = new Button();
                slotBtn.getStyleClass().add("slot-btn");

                final int slotNo = slot;
                switch (status) {
                    case FREE:
                        slotBtn.setText("Free");
                        slotBtn.getStyleClass().add("slot-free");
                        slotBtn.setOnAction(e -> handleSlotClicked(facility, slotNo, slotBtn));
                        break;
                    case BOOKED:
                        slotBtn.setText("Booked");
                        slotBtn.getStyleClass().add("slot-booked");
                        slotBtn.setDisable(true);
                        break;
                    case PENDING:
                        slotBtn.setText("Pending");
                        slotBtn.getStyleClass().add("slot-pending");
                        slotBtn.setDisable(true);
                        break;
                    case MAINTENANCE:
                        slotBtn.setText("Maint.");
                        slotBtn.getStyleClass().add("slot-maintenance");
                        slotBtn.setDisable(true);
                        break;
                    case PAST:
                        slotBtn.setText("Passed");
                        slotBtn.getStyleClass().add("slot-past");
                        slotBtn.setDisable(true);
                        break;
                }

                row.getChildren().add(slotBtn);
            }

            matrixContainer.getChildren().add(row);
        }
    }

    private void handleSlotClicked(Facility facility, int slotNo, Button btn) {
        // If clicking a slot on a different room, clear previous selection first
        if (selectedFacility != null && selectedFacility.getFacilityId() != facility.getFacilityId()) {
            clearSlotSelection();
        }

        selectedFacility = facility;

        if (selectedSlots.contains(slotNo)) {
            // Deselect
            selectedSlots.remove(slotNo);
            btn.getStyleClass().remove("slot-selected");
            btn.getStyleClass().add("slot-free");
            btn.setText("Free");
            activeSlotButtons.remove(slotNo);
        } else {
            // Select up to 3 slots (BR-03)
            if (selectedSlots.size() >= 3) {
                showAlert("Maximum 3 slots can be booked in a single request (BR-03).");
                return;
            }
            selectedSlots.add(slotNo);
            btn.getStyleClass().remove("slot-free");
            btn.getStyleClass().add("slot-selected");
            btn.setText("✓ Slot " + slotNo);
            activeSlotButtons.put(slotNo, btn);
        }

        updateSelectionBar();
    }

    private void updateSelectionBar() {
        if (selectedSlots.isEmpty() || selectedFacility == null) {
            selectionBar.setVisible(false);
            selectionBar.setManaged(false);
            return;
        }

        selectionBar.setVisible(true);
        selectionBar.setManaged(true);
        selectionFacilityLabel.setText("Selected Room: " + selectedFacility.getName() + " (" + selectedFacility.getBuilding() + ")");

        StringBuilder slotsDesc = new StringBuilder("Slots: ");
        int count = 0;
        for (int s : selectedSlots) {
            if (count > 0) slotsDesc.append(", ");
            slotsDesc.append("Slot ").append(s).append(" (").append(Booking.formatSlotTime(s)).append(")");
            count++;
        }
        selectionSlotsLabel.setText(slotsDesc + " | Total: " + selectedSlots.size() + " hr(s)");
    }

    @FXML
    private void handleClearSelection() {
        clearSlotSelection();
    }

    private void clearSlotSelection() {
        for (Map.Entry<Integer, Button> entry : activeSlotButtons.entrySet()) {
            Button btn = entry.getValue();
            btn.getStyleClass().remove("slot-selected");
            btn.getStyleClass().add("slot-free");
            btn.setText("Free");
        }
        selectedSlots.clear();
        activeSlotButtons.clear();
        selectedFacility = null;
        if (selectionBar != null) {
            selectionBar.setVisible(false);
            selectionBar.setManaged(false);
        }
    }

    // ---------------------------------------------------------------
    // Booking Confirmation Dialog
    // ---------------------------------------------------------------

    @FXML
    private void handleOpenBookingModal() {
        if (selectedFacility == null || selectedSlots.isEmpty()) {
            showAlert("Please select at least 1 free time slot to proceed.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fms/view/booking_dialog.fxml"));
            Parent root = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setTitle("Confirm Reservation — " + selectedFacility.getName());

            Label dlgFacilityLabel = (Label) root.lookup("#dlgFacilityLabel");
            Label dlgDateLabel = (Label) root.lookup("#dlgDateLabel");
            Label dlgSlotsLabel = (Label) root.lookup("#dlgSlotsLabel");
            Label dlgCapacityLabel = (Label) root.lookup("#dlgCapacityLabel");
            TextField dlgPurposeField = (TextField) root.lookup("#dlgPurposeField");
            TextField dlgAttendeesField = (TextField) root.lookup("#dlgAttendeesField");
            Label dlgErrorLabel = (Label) root.lookup("#dialogErrorLabel");
            HBox dlgApprovalNotice = (HBox) root.lookup("#dlgApprovalNotice");
            Button btnConfirm = (Button) root.lookup(".btn-primary");
            Button btnCancel = (Button) root.lookup(".btn-secondary");

            LocalDate date = datePicker.getValue();
            dlgFacilityLabel.setText(selectedFacility.getName() + " (" + selectedFacility.getBuilding() + ")");
            dlgDateLabel.setText(date.toString());
            dlgSlotsLabel.setText(selectedSlots.size() + " slot(s): " + selectedSlots.toString());
            dlgCapacityLabel.setText("Up to " + selectedFacility.getCapacity() + " persons");

            if (selectedFacility.isRequiresApproval()) {
                dlgApprovalNotice.setVisible(true);
                dlgApprovalNotice.setManaged(true);
            }

            btnCancel.setOnAction(e -> dialogStage.close());

            btnConfirm.setOnAction(e -> {
                String purpose = dlgPurposeField.getText();
                String attendeesStr = dlgAttendeesField.getText();

                if (purpose == null || purpose.trim().isEmpty()) {
                    showDialogError(dlgErrorLabel, "Please enter a purpose for this booking.");
                    return;
                }

                int attendees;
                try {
                    attendees = Integer.parseInt(attendeesStr.trim());
                } catch (Exception ex) {
                    showDialogError(dlgErrorLabel, "Attendees must be a valid number.");
                    return;
                }

                try {
                    List<Booking> created = bookingService.createBooking(
                            selectedFacility.getFacilityId(),
                            date,
                            new ArrayList<>(selectedSlots),
                            purpose,
                            attendees
                    );

                    dialogStage.close();
                    String statusNote = created.get(0).getStatus() == BookingStatus.Pending
                            ? " (Pending approval)"
                            : " (Approved)";
                    showSuccessToast("✓  Reserved " + created.size() + " slot(s) — " + selectedFacility.getName() + statusNote);
                    handleSearchSlots();

                } catch (BookingConflictException ex) {
                    showDialogError(dlgErrorLabel, ex.getMessage());
                } catch (InvalidInputException ex) {
                    showDialogError(dlgErrorLabel, ex.getMessage());
                } catch (Exception ex) {
                    showDialogError(dlgErrorLabel, "Error: " + ex.getMessage());
                }
            });

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/com/fms/css/slotwise-theme.css").toExternalForm());
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

        } catch (IOException e) {
            logger.error("Failed to open booking confirmation dialog: {}", e.getMessage(), e);
        }
    }

    private void showDialogError(Label label, String msg) {
        label.setText(msg);
        label.setVisible(true);
        label.setManaged(true);
    }

    // ---------------------------------------------------------------
    // View 2: My Bookings (FR-BKG-08)
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
    private void handleRefreshMyBookings() {
        List<Booking> bookings = bookingService.getMyBookings();
        myBookingsTable.setItems(FXCollections.observableArrayList(bookings));
    }

    private void handleCancelUserBooking(Booking b) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Are you sure you want to cancel your booking for " + b.getFacilityName() + " on " + b.getBookingDate() + " (Slot " + b.getSlotNo() + ")?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText("Confirm Cancellation");
        confirm.showAndWait().ifPresent(res -> {
            if (res == ButtonType.YES) {
                try {
                    bookingService.cancelBooking(b.getBookingId());
                    showAlert("Booking cancelled successfully.");
                    handleRefreshMyBookings();
                } catch (Exception ex) {
                    showAlert("Error cancelling booking: " + ex.getMessage());
                }
            }
        });
    }

    // ---------------------------------------------------------------
    // View 3: Admin Approvals (FR-BKG-07, FR-BKG-09)
    // ---------------------------------------------------------------

    private void setupAdminTable() {
        colAdminId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getBookingId())));
        colAdminUser.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUserFullName() + " (" + c.getValue().getUsername() + ")"));
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
                        Button approveBtn = new Button("Approve");
                        approveBtn.getStyleClass().add("btn-primary");
                        approveBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
                        approveBtn.setOnAction(e -> handleAdminApprove(b));

                        Button rejectBtn = new Button("Reject");
                        rejectBtn.getStyleClass().add("btn-danger");
                        rejectBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
                        rejectBtn.setOnAction(e -> handleAdminReject(b));

                        box.getChildren().addAll(approveBtn, rejectBtn);
                    } else if (b.getStatus() == BookingStatus.Approved && b.getBookingDate().isAfter(LocalDate.now())) {
                        Button cancelBtn = new Button("Cancel");
                        cancelBtn.getStyleClass().add("btn-secondary");
                        cancelBtn.setStyle("-fx-font-size: 11px; -fx-padding: 4 10;");
                        cancelBtn.setOnAction(e -> handleCancelUserBooking(b));
                        box.getChildren().add(cancelBtn);
                    }

                    setGraphic(box);
                }
            }
        });
    }

    @FXML
    private void handleFilterAdminBookings() {
        LocalDate date = adminDatePicker.getValue();
        String statusStr = adminStatusFilterCombo.getValue();
        BookingStatus status = null;
        if (statusStr != null && !statusStr.equals("All Statuses")) {
            status = BookingStatus.valueOf(statusStr);
        }

        List<Booking> list = bookingService.searchAllBookings(date, null, null, status);
        adminBookingsTable.setItems(FXCollections.observableArrayList(list));
    }

    @FXML
    private void handleResetAdminFilters() {
        adminDatePicker.setValue(null);
        adminStatusFilterCombo.setValue("All Statuses");
        handleFilterAdminBookings();
    }

    private void handleAdminApprove(Booking b) {
        try {
            bookingService.approveBooking(b.getBookingId());
            showAlert("Booking ID " + b.getBookingId() + " approved successfully.");
            handleFilterAdminBookings();
        } catch (Exception e) {
            showAlert("Error approving booking: " + e.getMessage());
        }
    }

    private void handleAdminReject(Booking b) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/fms/view/booking_reject_dialog.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Reject Booking #" + b.getBookingId());

            TextArea reasonArea = (TextArea) root.lookup("#rejectionReasonArea");
            Button btnCancel = (Button) root.lookup(".btn-secondary");
            Button btnReject = (Button) root.lookup(".btn-danger");

            btnCancel.setOnAction(e -> stage.close());
            btnReject.setOnAction(e -> {
                String reason = reasonArea.getText();
                try {
                    bookingService.rejectBooking(b.getBookingId(), reason != null ? reason.trim() : "");
                    stage.close();
                    showAlert("Booking rejected.");
                    handleFilterAdminBookings();
                } catch (Exception ex) {
                    showAlert("Error rejecting booking: " + ex.getMessage());
                }
            });

            Scene scene = new Scene(root);
            scene.getStylesheets().add(getClass().getResource("/com/fms/css/slotwise-theme.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();

        } catch (IOException e) {
            logger.error("Failed to open rejection dialog: {}", e.getMessage(), e);
        }
    }

    private String getStatusBadgeClass(BookingStatus status) {
        if (status == null) return "badge-status-completed";
        switch (status) {
            case Approved: return "badge-status-approved";
            case Pending: return "badge-status-pending";
            case Rejected: return "badge-status-rejected";
            case Cancelled: return "badge-status-cancelled";
            case Completed: return "badge-status-completed";
            default: return "badge-status-completed";
        }
    }

    // Feedback helpers
    private void showAlert(String msg) {
        alertMessageLabel.setText(msg);
        alertBanner.setVisible(true);
        alertBanner.setManaged(true);
    }

    /**
     * Shows an animated sage-green toast pill at the top-right of the booking view.
     * Plays a subtle system beep for audio feedback, then auto-dismisses after 2.5 s.
     * Integrates with Module 7 Notifications Engine pattern (FR-BKG-08).
     */
    private void showSuccessToast(String message) {
        // Subtle audio cue
        try { java.awt.Toolkit.getDefaultToolkit().beep(); } catch (Exception ignored) {}

        // Build the toast label
        Label toast = new Label(message);
        toast.getStyleClass().add("toast-success");
        toast.setMaxWidth(500);
        toast.setWrapText(false);
        toast.setTranslateY(-60); // start above view
        toast.setOpacity(0);

        // Overlay in a StackPane anchored top-right
        StackPane overlay = new StackPane(toast);
        StackPane.setAlignment(toast, Pos.TOP_CENTER);
        StackPane.setMargin(toast, new Insets(16, 0, 0, 0));
        overlay.setPickOnBounds(false);
        overlay.setMouseTransparent(true);

        // Grab the root VBox from the scene via alertBanner's scene
        if (alertBanner.getScene() == null) { showAlert(message); return; }
        Parent sceneRoot = alertBanner.getScene().getRoot();
        if (!(sceneRoot instanceof Pane rootPane)) { showAlert(message); return; }

        rootPane.getChildren().add(overlay);
        overlay.prefWidthProperty().bind(rootPane.widthProperty());
        overlay.prefHeightProperty().bind(rootPane.heightProperty());

        // Slide-in + Fade-in in parallel (simultaneous entrance)
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), toast);
        slideIn.setFromY(-60);
        slideIn.setToY(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        javafx.animation.ParallelTransition enter = new javafx.animation.ParallelTransition(slideIn, fadeIn);

        // Hold
        PauseTransition hold = new PauseTransition(Duration.millis(2000));

        // Fade-out
        FadeTransition fadeOut = new FadeTransition(Duration.millis(350), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);

        SequentialTransition seq = new SequentialTransition(enter, hold, fadeOut);
        seq.setOnFinished(e -> rootPane.getChildren().remove(overlay));
        seq.play();

        logger.info("Toast shown: {}", message);

    }

    @FXML
    private void handleDismissAlert() {
        alertBanner.setVisible(false);
        alertBanner.setManaged(false);
    }
}
