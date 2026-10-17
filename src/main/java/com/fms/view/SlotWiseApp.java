package com.fms.view;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;

/**
 * Main JavaFX Application class for SlotWise.
 * Manages the primary stage and screen transitions between Login and Dashboard.
 */
public class SlotWiseApp extends Application {

    private static final Logger logger = LoggerFactory.getLogger(SlotWiseApp.class);

    private static SlotWiseApp instance;
    private Stage primaryStage;

    public SlotWiseApp() {
        instance = this;
    }

    public static SlotWiseApp getInstance() {
        return instance;
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.primaryStage.setTitle("SlotWise — Facility Management System");
        this.primaryStage.setMinWidth(960);
        this.primaryStage.setMinHeight(620);

        // Show the login screen on startup
        showLogin();
        this.primaryStage.show();
    }

    /**
     * Loads and displays the login screen.
     */
    public void showLogin() {
        try {
            URL fxmlUrl = getClass().getResource("/com/fms/view/login.fxml");
            if (fxmlUrl == null) {
                logger.error("Could not find login.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Scene scene = new Scene(root, 960, 620);
            applyStylesheet(scene);

            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
            logger.info("Login screen displayed.");

        } catch (IOException e) {
            logger.error("Failed to load login screen: {}", e.getMessage(), e);
        }
    }

    /**
     * Loads and displays the dashboard screen.
     */
    public void showDashboard() {
        try {
            URL fxmlUrl = getClass().getResource("/com/fms/view/dashboard.fxml");
            if (fxmlUrl == null) {
                logger.error("Could not find dashboard.fxml");
                return;
            }

            FXMLLoader loader = new FXMLLoader(fxmlUrl);
            Parent root = loader.load();

            Scene scene = new Scene(root, 1080, 700);
            applyStylesheet(scene);

            primaryStage.setScene(scene);
            primaryStage.centerOnScreen();
            logger.info("Dashboard screen displayed.");

        } catch (IOException e) {
            logger.error("Failed to load dashboard screen: {}", e.getMessage(), e);
        }
    }

    /**
     * Applies the SlotWise stylesheet to a scene.
     */
    private void applyStylesheet(Scene scene) {
        URL cssUrl = getClass().getResource("/com/fms/css/slotwise-theme.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            logger.warn("Could not find slotwise-theme.css");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
