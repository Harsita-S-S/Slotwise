package com.fms.controller;

import com.fms.dao.BookingDAO;
import com.fms.dao.FacilityDAO;
import com.fms.model.Role;
import com.fms.service.SessionManager;
import com.fms.view.SlotWiseApp;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the main dashboard screen.
 * Shows role-specific menu items and content based on the logged-in user.
 *
 * Covers:
 * - FR-AUTH-03: Role-specific dashboard and menu after login.
 * - FR-AUTH-04: Reads the session to display user info.
 * - FR-AUTH-05: Hides menu items not permitted for the user's role.
 */
public class DashboardController {

    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);

    // Sidebar labels
    @FXML private Label brandTitle;
    @FXML private Label brandSubtitle;
    @FXML private Label userNameLabel;
    @FXML private Label userRoleLabel;

    // Sidebar navigation buttons
    @FXML private Button navDashboard;
    @FXML private Button navFacilities;
    @FXML private Button navBookings;
    @FXML private Button navMaintenance;
    @FXML private Button navEquipment;
    @FXML private Button navStaff;
    @FXML private Button navUsers;
    @FXML private Button navReports;
    @FXML private Button logoutButton;

    // Main content area
    @FXML private VBox contentArea;
    @FXML private Label welcomeLabel;
    @FXML private Label roleInfoLabel;

    // Live stat card labels (populated from DB)
    @FXML private Label statFacilitiesCount;
    @FXML private Label statBookingsCount;
    @FXML private Label statPendingCount;

    private final FacilityDAO facilityDAO = new FacilityDAO();
    private final BookingDAO bookingDAO = new BookingDAO();

    private java.util.List<javafx.scene.Node> defaultDashboardNodes;

    /**
     * Called automatically by JavaFX after the FXML is loaded.
     * Sets up the sidebar based on the current user's role.
     */
    @FXML
    public void initialize() {
        // Save initial overview cards
        defaultDashboardNodes = new java.util.ArrayList<>(contentArea.getChildren());

        SessionManager session = SessionManager.getInstance();

        // Display user info in sidebar (FR-AUTH-04)
        userNameLabel.setText(session.getCurrentUserDisplayName());
        userRoleLabel.setText(session.getCurrentRole().name());

        // Display welcome message
        welcomeLabel.setText("Welcome, " + session.getCurrentUserDisplayName() + "!");

        // Set up role-specific menu visibility (FR-AUTH-03, FR-AUTH-05)
        setupMenuForRole(session.getCurrentRole());

        // Highlight the Dashboard nav button as active
        setActiveNavButton(navDashboard);

        // Show role-specific info on the dashboard
        showRoleDashboardInfo(session.getCurrentRole());

        // Populate live stat counters
        refreshDashboardStats();
    }

    /**
     * Configures which menu items are visible based on the user's role (FR-AUTH-05).
     * Admin sees everything. Staff sees facilities, bookings, maintenance, equipment.
     * User sees only bookings and facilities (read-only).
     */
    private void setupMenuForRole(Role role) {
        // Start by hiding admin-only items
        navUsers.setVisible(false);
        navUsers.setManaged(false);
        navReports.setVisible(false);
        navReports.setManaged(false);
        navStaff.setVisible(false);
        navStaff.setManaged(false);

        if (role == Role.Admin) {
            // Admin can see everything
            navUsers.setVisible(true);
            navUsers.setManaged(true);
            navReports.setVisible(true);
            navReports.setManaged(true);
            navStaff.setVisible(true);
            navStaff.setManaged(true);

        } else if (role == Role.Staff) {
            // Staff can see staff-related items
            navStaff.setVisible(true);
            navStaff.setManaged(true);
        }
        // User role: only default items (Dashboard, Facilities, Bookings, Maintenance, Equipment)
    }

    /**
     * Shows role-specific information on the main dashboard content area.
     */
    private void showRoleDashboardInfo(Role role) {
        if (role == Role.Admin) {
            roleInfoLabel.setText("You have full administrative access. " +
                    "Manage users, facilities, bookings, and system reports.");
        } else if (role == Role.Staff) {
            roleInfoLabel.setText("You can manage assigned maintenance tasks, " +
                    "view facilities, and handle equipment.");
        } else {
            roleInfoLabel.setText("You can browse available facilities, " +
                    "make bookings, and track your requests.");
        }
    }

    // ---------------------------------------------------------------
    // Navigation Button Handlers
    // ---------------------------------------------------------------

    @FXML
    private void handleNavDashboard() {
        setActiveNavButton(navDashboard);
        if (defaultDashboardNodes != null) {
            contentArea.getChildren().setAll(defaultDashboardNodes);
        }
        // Refresh live counts every time the user returns to Dashboard
        refreshDashboardStats();
        logger.info("Navigated to: Dashboard");
    }

    /**
     * Loads live counts from BookingDAO and FacilityDAO and sets them on the
     * stat card labels on the Home Dashboard.
     */
    private void refreshDashboardStats() {
        try {
            int facilities = facilityDAO.findAllActive().size();
            int todayBookings = bookingDAO.countTodayActiveBookings();
            int pending = bookingDAO.countPendingApprovals();

            if (statFacilitiesCount != null) statFacilitiesCount.setText(String.valueOf(facilities));
            if (statBookingsCount   != null) statBookingsCount.setText(String.valueOf(todayBookings));
            if (statPendingCount    != null) statPendingCount.setText(String.valueOf(pending));

            logger.debug("Dashboard stats refreshed — facilities={}, todayBookings={}, pending={}",
                    facilities, todayBookings, pending);
        } catch (Exception e) {
            logger.error("Failed to refresh dashboard stats: {}", e.getMessage());
        }
    }

    @FXML
    private void handleNavFacilities() {
        setActiveNavButton(navFacilities);
        loadView("/com/fms/view/facilities.fxml");
        logger.info("Navigated to: Facilities");
    }

    private void loadView(String fxmlPath) {
        try {
            java.net.URL fxmlUrl = getClass().getResource(fxmlPath);
            if (fxmlUrl != null) {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(fxmlUrl);
                javafx.scene.Parent view = loader.load();
                contentArea.getChildren().setAll(view);
            } else {
                logger.error("FXML file not found: {}", fxmlPath);
                showEmptyView("File Not Found", "The view template " + fxmlPath + " could not be found.");
            }
        } catch (Exception e) {
            logger.error("Failed to load view {}: {}", fxmlPath, e.getMessage(), e);
            showEmptyView("Unable to Load View", "Error: " + e.getMessage());
        }
    }

    private void showEmptyView(String title, String subtitle) {
        javafx.scene.layout.VBox emptyBox = new javafx.scene.layout.VBox(12);
        emptyBox.setAlignment(javafx.geometry.Pos.CENTER);
        emptyBox.setPadding(new javafx.geometry.Insets(80, 24, 80, 24));
        emptyBox.getStyleClass().add("card");
        emptyBox.setMaxWidth(Double.MAX_VALUE);

        javafx.scene.control.Label titleLabel = new javafx.scene.control.Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #4A3E3D;");

        javafx.scene.control.Label subtitleLabel = new javafx.scene.control.Label(subtitle);
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #7A6B66;");

        emptyBox.getChildren().addAll(titleLabel, subtitleLabel);
        contentArea.getChildren().setAll(emptyBox);
    }

    @FXML
    private void handleNavBookings() {
        setActiveNavButton(navBookings);
        loadView("/com/fms/view/bookings.fxml");
        logger.info("Navigated to: Bookings");
    }

    @FXML
    private void handleNavMaintenance() {
        setActiveNavButton(navMaintenance);
        loadView("/com/fms/view/maintenance.fxml");
        logger.info("Navigated to: Maintenance");
    }

    @FXML
    private void handleNavEquipment() {
        setActiveNavButton(navEquipment);
        loadView("/com/fms/view/equipment.fxml");
        logger.info("Navigated to: Equipment");
    }

    @FXML
    private void handleNavStaff() {
        setActiveNavButton(navStaff);
        loadView("/com/fms/view/staff.fxml");
        logger.info("Navigated to: Staff");
    }

    @FXML
    private void handleNavUsers() {
        SessionManager.getInstance().requireRole(Role.Admin);
        setActiveNavButton(navUsers);
        loadView("/com/fms/view/users.fxml");
        logger.info("Navigated to: User Management");
    }

    @FXML
    private void handleNavReports() {
        SessionManager.getInstance().requireRole(Role.Admin);
        setActiveNavButton(navReports);
        showEmptyView("Reports & Analytics", "This module has not been implemented yet.");
        logger.info("Navigated to: Reports");
    }

    /**
     * Handles the Logout button click.
     * Clears the session and returns to the login screen.
     */
    @FXML
    private void handleLogout() {
        logger.info("User '{}' logging out", SessionManager.getInstance().getCurrentUser().getUsername());
        SessionManager.getInstance().logout();

        try {
            SlotWiseApp.getInstance().showLogin();
        } catch (Exception e) {
            logger.error("Failed to return to login screen: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // UI Helper Methods
    // ---------------------------------------------------------------

    /**
     * Highlights the active navigation button and removes highlight from others.
     */
    private void setActiveNavButton(Button activeButton) {
        // List all nav buttons
        Button[] allNavButtons = {
            navDashboard, navFacilities, navBookings,
            navMaintenance, navEquipment, navStaff, navUsers, navReports
        };

        // Remove active style from all buttons
        for (Button btn : allNavButtons) {
            btn.getStyleClass().remove("sidebar-btn-active");
            if (!btn.getStyleClass().contains("sidebar-btn")) {
                btn.getStyleClass().add("sidebar-btn");
            }
        }

        // Add active style to the selected button
        activeButton.getStyleClass().add("sidebar-btn-active");
    }
}
