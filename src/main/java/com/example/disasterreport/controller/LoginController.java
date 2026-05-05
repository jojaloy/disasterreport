package com.example.disasterreport.controller;

import com.example.disasterreport.App;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class LoginController {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;
    @FXML private Button        loginButton;

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

            // Load the FXML first so the controller is wired up
            javafx.scene.Parent root = loader.load();

            // Resize the window BEFORE showing the new scene
            Stage stage = (Stage) loginButton.getScene().getWindow();
            App.resizeForMain(stage);

            Scene scene = new Scene(root, 1024, 680);
            stage.setScene(scene);
            stage.setTitle("Disaster Report System");

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