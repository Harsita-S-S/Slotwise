package com.fms.controller;

import com.fms.model.Notification;
import com.fms.model.NotificationType;
import com.fms.service.NotificationService;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Notification panel (FR-NOT-04): the logged-in user's notifications with an unread filter,
 * mark-as-read and mark-all-read. Only the user's own notifications are ever shown.
 */
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @FXML private Label summaryLabel;
    @FXML private CheckBox unreadOnlyCheck;
    @FXML private Button markAllReadButton;
    @FXML private ListView<Notification> notificationList;

    private final NotificationService notificationService = new NotificationService();

    /** Called after any change so the sidebar unread badge can refresh. */
    private Runnable onChange = () -> { };

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange != null ? onChange : () -> { };
    }

    @FXML
    public void initialize() {
        notificationList.setPlaceholder(new Label("You're all caught up. No notifications to show."));
        notificationList.setCellFactory(lv -> new NotificationCell());
        loadNotifications();
    }

    @FXML
    private void handleRefresh() {
        loadNotifications();
        onChange.run();
    }

    @FXML
    private void handleUnreadOnlyToggle() {
        loadNotifications();
    }

    @FXML
    private void handleMarkAllRead() {
        try {
            int changed = notificationService.markAllRead();
            logger.info("Marked {} notifications as read", changed);
        } catch (Exception e) {
            logger.error("Failed to mark all notifications read: {}", e.getMessage(), e);
        }
        loadNotifications();
        onChange.run();
    }

    private void markRead(Notification notification) {
        try {
            notificationService.markRead(notification.getNotifId());
        } catch (Exception e) {
            logger.error("Failed to mark notification {} read: {}", notification.getNotifId(), e.getMessage(), e);
        }
        loadNotifications();
        onChange.run();
    }

    private void loadNotifications() {
        try {
            List<Notification> items = notificationService.getMyNotifications(unreadOnlyCheck.isSelected());
            notificationList.getItems().setAll(items);
            int unread = notificationService.getMyUnreadCount();
            summaryLabel.setText(unread == 0 ? "No unread notifications" : unread + " unread");
            markAllReadButton.setDisable(unread == 0);
        } catch (Exception e) {
            logger.error("Failed to load notifications: {}", e.getMessage(), e);
            summaryLabel.setText("Unable to load notifications");
        }
    }

    /** One notification row: icon, title, message, time, and a "Mark read" button when unread. */
    private class NotificationCell extends ListCell<Notification> {
        NotificationCell() {
            getStyleClass().add("notif-cell");
        }

        @Override
        protected void updateItem(Notification n, boolean empty) {
            super.updateItem(n, empty);
            if (empty || n == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            NotificationType type = NotificationType.fromString(n.getType());

            Label icon = new Label(type.getIcon());
            icon.getStyleClass().add("notif-icon");

            Label title = new Label(type.getTitle());
            title.getStyleClass().add(n.isRead() ? "notif-title" : "notif-title-unread");
            HBox titleRow = new HBox(6, title);
            titleRow.setAlignment(Pos.CENTER_LEFT);
            if (!n.isRead()) {
                Label dot = new Label("\u25CF");
                dot.getStyleClass().add("notif-dot");
                titleRow.getChildren().add(dot);
            }

            Label message = new Label(n.getMessage());
            message.setWrapText(true);
            message.getStyleClass().add("notif-message");
            if (getListView() != null) {
                message.maxWidthProperty().bind(getListView().widthProperty().subtract(200));
            }

            Label time = new Label(n.getCreatedAt() == null ? "" : n.getCreatedAt().format(TIME_FORMAT));
            time.getStyleClass().add("card-subtitle");

            VBox body = new VBox(3, titleRow, message, time);
            HBox.setHgrow(body, Priority.ALWAYS);

            HBox row = new HBox(12, icon, body);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add(n.isRead() ? "notif-row" : "notif-row-unread");

            if (!n.isRead()) {
                Button markReadButton = new Button("Mark read");
                markReadButton.getStyleClass().add("btn-secondary");
                markReadButton.setOnAction(e -> markRead(n));
                row.getChildren().add(markReadButton);
            }

            setText(null);
            setGraphic(row);
        }
    }
}
