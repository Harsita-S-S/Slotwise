package com.fms.controller;

import com.fms.model.Role;
import com.fms.model.User;
import com.fms.service.AuthService;
import com.fms.service.SessionManager;
import com.fms.util.PasswordUtil;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/** Admin-only user management screen (FR-AUTH-06). */
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @FXML private TextField searchField;
    @FXML private ComboBox<String> roleFilter;
    @FXML private ComboBox<String> statusFilter;
    @FXML private TableView<User> userTable;
    @FXML private TableColumn<User, String> colName;
    @FXML private TableColumn<User, String> colUsername;
    @FXML private TableColumn<User, String> colEmail;
    @FXML private TableColumn<User, String> colRole;
    @FXML private TableColumn<User, String> colStatus;
    @FXML private TableColumn<User, Void> colActions;

    private final AuthService authService = new AuthService();
    private final ObservableList<User> masterList = FXCollections.observableArrayList();
    private FilteredList<User> filteredList;

    @FXML
    public void initialize() {
        SessionManager.getInstance().requireRole(Role.Admin); // FR-AUTH-05

        roleFilter.setItems(FXCollections.observableArrayList("All roles", "Admin", "Staff", "User"));
        roleFilter.setValue("All roles");
        statusFilter.setItems(FXCollections.observableArrayList("All statuses", "Active", "Inactive", "Locked"));
        statusFilter.setValue("All statuses");

        colName.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getFullName()));
        colUsername.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getUsername()));
        colEmail.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getEmail()));
        colRole.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getRole().name()));
        colStatus.setCellValueFactory(c -> new ReadOnlyStringWrapper(statusOf(c.getValue())));
        setupActionsColumn();

        filteredList = new FilteredList<>(masterList, u -> true);
        userTable.setItems(filteredList);

        searchField.textProperty().addListener((o, a, b) -> applyFilters());
        roleFilter.valueProperty().addListener((o, a, b) -> applyFilters());
        statusFilter.valueProperty().addListener((o, a, b) -> applyFilters());

        loadUsers();
    }

    private void setupActionsColumn() {
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button resetBtn = new Button("Reset Password");
            private final Button toggleBtn = new Button();
            private final HBox box = new HBox(8, resetBtn, toggleBtn);

            {
                resetBtn.getStyleClass().add("btn-secondary");
                box.setAlignment(Pos.CENTER_LEFT);
                resetBtn.setOnAction(e -> handleResetPassword(getTableView().getItems().get(getIndex())));
                toggleBtn.setOnAction(e -> handleToggleActive(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                User u = getTableView().getItems().get(getIndex());
                toggleBtn.setText(u.isActive() ? "Deactivate" : "Reactivate");
                toggleBtn.getStyleClass().removeAll("btn-danger", "btn-secondary");
                toggleBtn.getStyleClass().add(u.isActive() ? "btn-danger" : "btn-secondary");
                setGraphic(box);
            }
        });
    }

    private void loadUsers() {
        Task<List<User>> task = new Task<>() {
            @Override
            protected List<User> call() {
                return authService.getAllUsers();
            }
        };
        task.setOnSucceeded(e -> {
            masterList.setAll(task.getValue());
            applyFilters();
            userTable.refresh();
        });
        task.setOnFailed(e -> {
            logger.error("Failed to load users: {}", task.getException().getMessage(), task.getException());
            showError("Failed to load users: " + task.getException().getMessage());
        });
        Thread t = new Thread(task, "user-loader");
        t.setDaemon(true);
        t.start();
    }

    private void applyFilters() {
        String q = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
        String role = roleFilter.getValue();
        String status = statusFilter.getValue();

        filteredList.setPredicate(u -> {
            if (!q.isEmpty() && !(contains(u.getFullName(), q) || contains(u.getUsername(), q)
                    || contains(u.getEmail(), q))) {
                return false;
            }
            if (role != null && !role.startsWith("All") && !u.getRole().name().equals(role)) {
                return false;
            }
            if (status != null && !status.startsWith("All") && !statusOf(u).equals(status)) {
                return false;
            }
            return true;
        });
    }

    private boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    private String statusOf(User u) {
        if (!u.isActive()) return "Inactive";
        if (PasswordUtil.isAccountLocked(u.getLockedUntil())) return "Locked";
        return "Active";
    }

    @FXML
    private void handleResetFilters() {
        searchField.clear();
        roleFilter.setValue("All roles");
        statusFilter.setValue("All statuses");
    }

    @FXML
    private void handleAddUser() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add User");
        dialog.setHeaderText("Create a new login account");
        ButtonType createType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField fullName = new TextField();
        TextField username = new TextField();
        TextField email = new TextField();
        PasswordField password = new PasswordField();
        ComboBox<Role> role = new ComboBox<>(FXCollections.observableArrayList(Role.values()));
        role.setValue(Role.User);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Full name"), fullName);
        grid.addRow(1, new Label("Username"), username);
        grid.addRow(2, new Label("Email"), email);
        grid.addRow(3, new Label("Role"), role);
        grid.addRow(4, new Label("Temporary password"), password);
        dialog.getDialogPane().setContent(grid);

        // Validate on click: on failure, keep the dialog open and show the message (FR-VAL-01)
        Button createBtn = (Button) dialog.getDialogPane().lookupButton(createType);
        createBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                authService.createUser(username.getText(), password.getText(), role.getValue(),
                        fullName.getText(), email.getText());
            } catch (RuntimeException ex) {
                ev.consume();
                showError(ex.getMessage());
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == createType) {
            loadUsers();
        }
    }

    private void handleResetPassword(User user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Reset Password");
        dialog.setHeaderText("Set a new password for " + user.getUsername());
        ButtonType okType = new ButtonType("Reset", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(okType, ButtonType.CANCEL);

        PasswordField pw = new PasswordField();
        pw.setPromptText("New password (min 8 characters)");
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(16));
        grid.add(pw, 0, 0);
        dialog.getDialogPane().setContent(grid);

        Button okBtn = (Button) dialog.getDialogPane().lookupButton(okType);
        okBtn.addEventFilter(ActionEvent.ACTION, ev -> {
            try {
                authService.resetPassword(user.getUserId(), pw.getText());
            } catch (RuntimeException ex) {
                ev.consume();
                showError(ex.getMessage());
            }
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == okType) {
            loadUsers();
            new Alert(Alert.AlertType.INFORMATION,
                    "Password reset. Any lockout on the account was cleared.").showAndWait();
        }
    }

    private void handleToggleActive(User user) {
        boolean deactivating = user.isActive();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                (deactivating ? "Deactivate " : "Reactivate ") + user.getUsername() + "?",
                ButtonType.YES, ButtonType.NO);
        confirm.setHeaderText(null);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.YES) {
            return;
        }
        try {
            if (deactivating) {
                authService.deactivateUser(user.getUserId());
            } else {
                authService.reactivateUser(user.getUserId());
            }
            loadUsers();
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}