package com.example.disasterreport.controller;

import com.example.disasterreport.model.Reporter; // OOP UPDATE: Import Reporter
import com.example.disasterreport.util.DatabaseManager; // OOP UPDATE: Import DB Manager
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class RegisterController implements Initializable {

    @FXML private TextField     fullNameField;
    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         validationLabel;
    @FXML private Label         successLabel;
    @FXML private Label         statusLabel;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setValidation(false, null);
        setSuccess(false, null);
    }

    // ── Handlers ─────────────────────────────────────────────────────────────

    @FXML
    private void handleRegister() {
        setValidation(false, null);
        setSuccess(false, null);

        String username        = usernameField.getText().trim();
        String password        = passwordField.getText().trim();
        String confirmPassword = confirmPasswordField.getText().trim();

        // ── Validation ────────────────────────────────────────────────────
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            setValidation(true, "Please fill in all required fields (*).");
            return;
        }

        if (username.length() < 3) {
            setValidation(true, "Username must be at least 3 characters.");
            return;
        }

        if (password.length() < 6) {
            setValidation(true, "Password must be at least 6 characters.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            setValidation(true, "Passwords do not match.");
            return;
        }

        // ── Persist (OOP UPDATE: Generate a Reporter Object) ───────────────
        // ID is 0 (auto-incremented by DB), default Trust Score is 100
        Reporter newReporter = new Reporter(0, username, password, 100);

        // Pass the object directly to our DatabaseManager
        boolean registered = DatabaseManager.getInstance().saveUser(newReporter);

        if (registered) {
            com.example.disasterreport.util.ModernDialog.showMessage(
                    "Registration Successful",
                    "Account created successfully! You will now be redirected to the login page.",
                    false
            );
            handleBackToLogin();
        } else {
            setValidation(true, "Username already exists. Please choose another.");
        }
    }

    @FXML
    private void handleBackToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/disasterreport/LoginView.fxml")
            );
            Scene scene = new Scene(loader.load());
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Disaster Report System – Login");
        } catch (Exception e) {
            e.printStackTrace();
            setValidation(true, "Could not load login screen.");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setValidation(boolean visible, String message) {
        validationLabel.setVisible(visible);
        validationLabel.setManaged(visible);
        if (message != null) validationLabel.setText(message);
    }

    private void setSuccess(boolean visible, String message) {
        successLabel.setVisible(visible);
        successLabel.setManaged(visible);
        if (message != null) successLabel.setText(message);
    }

    private void clearFields() {
        fullNameField.clear();
        usernameField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }
}