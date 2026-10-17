package com.fms.controller;

import com.fms.exception.AuthenticationException;
import com.fms.model.User;
import com.fms.service.AuthService;
import com.fms.service.SessionManager;
import com.fms.view.SlotWiseApp;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller for the login screen.
 * Handles user input, calls AuthService, and navigates to the dashboard on success.
 *
 * Covers:
 * - FR-AUTH-01: Login screen with username and password.
 * - FR-AUTH-03: Routes to role-specific dashboard after login.
 * - FR-AUTH-04: Sets session via SessionManager.
 * - FR-AUTH-07: Displays lockout error messages.
 */
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Button signInButton;
    @FXML private Label errorLabel;
    @FXML private VBox loginCard;

    private final AuthService authService;

    public LoginController() {
        this.authService = new AuthService();
    }

    /**
     * Called automatically by JavaFX after the FXML is loaded.
     * Sets up the initial state and animations.
     */
    @FXML
    public void initialize() {
        // Hide the error label initially
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        // Play a fade-in animation on the login card
        playCardFadeIn();

        // Allow pressing Enter in the password field to trigger sign in
        passwordField.setOnAction(event -> handleSignIn());
    }

    /**
     * Handles the Sign In button click.
     * Validates input, calls AuthService, and navigates on success.
     */
    @FXML
    private void handleSignIn() {
        // Clear any previous error
        hideError();

        // Get the entered values
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Basic input validation
        if (username.isEmpty()) {
            showError("Please enter your username.");
            usernameField.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            showError("Please enter your password.");
            passwordField.requestFocus();
            return;
        }

        // Disable the button while processing
        signInButton.setDisable(true);
        signInButton.setText("Signing in...");

        try {
            // Attempt to authenticate
            User authenticatedUser = authService.login(username, password);

            // Store the user in the session (FR-AUTH-04)
            SessionManager.getInstance().setCurrentUser(authenticatedUser);

            logger.info("Login successful for user: {} (role: {})",
                    authenticatedUser.getUsername(), authenticatedUser.getRole());

            // Navigate to the role-specific dashboard (FR-AUTH-03)
            navigateToDashboard();

        } catch (AuthenticationException e) {
            // Show the error message from AuthService
            showError(e.getMessage());

            // If the account is locked, also shake the card for visual feedback
            if (e.isLocked()) {
                playShakeAnimation();
            }

            // Clear the password field for security
            passwordField.clear();
            passwordField.requestFocus();

        } finally {
            // Re-enable the button
            signInButton.setDisable(false);
            signInButton.setText("Sign In");
        }
    }

    /**
     * Navigates to the dashboard screen.
     * The dashboard will show different content based on the user's role (FR-AUTH-03).
     */
    private void navigateToDashboard() {
        try {
            SlotWiseApp.getInstance().showDashboard();
        } catch (Exception e) {
            logger.error("Failed to navigate to dashboard: {}", e.getMessage(), e);
            showError("An unexpected error occurred. Please try again.");
        }
    }

    // ---------------------------------------------------------------
    // UI Helper Methods
    // ---------------------------------------------------------------

    /**
     * Shows an error message below the form fields.
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);

        // Fade in the error label
        FadeTransition fade = new FadeTransition(Duration.millis(200), errorLabel);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    /**
     * Hides the error message.
     */
    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    /**
     * Plays a fade-in animation on the login card when the screen loads.
     */
    private void playCardFadeIn() {
        loginCard.setOpacity(0.0);

        // Slide up slightly while fading in
        TranslateTransition slide = new TranslateTransition(Duration.millis(500), loginCard);
        slide.setFromY(20);
        slide.setToY(0);

        FadeTransition fade = new FadeTransition(Duration.millis(500), loginCard);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);

        slide.play();
        fade.play();
    }

    /**
     * Plays a subtle horizontal shake animation on the login card (for locked accounts).
     */
    private void playShakeAnimation() {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), loginCard);
        shake.setFromX(0);
        shake.setToX(8);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(event -> loginCard.setTranslateX(0));
        shake.play();
    }
}
