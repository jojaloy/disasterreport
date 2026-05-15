package com.example.disasterreport.controller;

import com.example.disasterreport.App;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class LoginController implements Initializable {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField     passwordTextField;
    @FXML private CheckBox      showPasswordCheckBox;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Bind the text properties so both fields share the exact same text automatically
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
    }

    @FXML
    private void togglePasswordVisibility() {
        if (showPasswordCheckBox.isSelected()) {
            passwordTextField.setVisible(true);
            passwordTextField.setManaged(true);
            passwordField.setVisible(false);
            passwordField.setManaged(false);
        } else {
            passwordTextField.setVisible(false);
            passwordTextField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
        }
    }

    @FXML
    private void handleForgotPassword() {
        java.util.Optional<String> result = com.example.disasterreport.util.ModernDialog.showTextInput(
                "Forgot Password",
                "Enter your username to request a reset:",
                ""
        );

        result.ifPresent(username -> {
            if (!username.trim().isEmpty()) {
                DatabaseManager.getInstance().addRequest(username.trim(), "PASSWORD_RESET", "User requested password reset");
                com.example.disasterreport.util.ModernDialog.showMessage("Request Sent", "Your reset request has been sent. Please wait for an Admin to provide your temporary password.", false);
            }
        });
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password.");
            return;
        }

        User found = DatabaseManager.getInstance().validateUser(username, password);

        if (found != null) {
            loadMainView(found);
        } else {
            showError("Invalid username or password.");
        }
    }

    @FXML
    private void handleGoToRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/disasterreport/RegisterView.fxml")
            );
            Scene scene = new Scene(loader.load(), 480, 700);
            Stage stage = (Stage) loginButton.getScene().getWindow();
            stage.setScene(scene);
            stage.setMinWidth(480);
            stage.setMinHeight(700);
            stage.setTitle("Disaster Report System – Register");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not open registration page.");
        }
    }

    private void loadMainView(User user) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/disasterreport/MainView.fxml")
            );

            javafx.scene.Parent root = loader.load();
            Stage stage = (Stage) loginButton.getScene().getWindow();

            // Load the new scene WITHOUT forcing the 1024x680 dimensions
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setTitle("Disaster Report System");

            // Apply the maximized state AFTER the scene is set
            App.resizeForMain(stage);

            MainController ctrl = loader.getController();
            ctrl.setCurrentUser(user);

        } catch (Exception e) {
            e.printStackTrace();
            showError("Could not load main view.");
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }
}