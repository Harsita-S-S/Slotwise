package com.fms;

import com.fms.util.DBConnection;
import com.fms.util.DatabaseInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main application launcher for the Facility Management System (FMS).
 */
public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Initializing Facility Management System (FMS)...");

        // Verify and seed database on startup
        try {
            DatabaseInitializer.initializeDatabase();
            logger.info("Database initialized and ready.");
        } catch (Exception e) {
            logger.error("Failed to initialize database on startup: {}", e.getMessage(), e);
        }

        // Add shutdown hook to close connection pool gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Application shutting down. Closing resources...");
            DBConnection.closePool();
        }));

        logger.info("Starting SlotWise JavaFX UI...");
        javafx.application.Application.launch(com.fms.view.SlotWiseApp.class, args);
    }
}
