package com.example.disasterreport.controller;

import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import com.example.disasterreport.util.ModernDialog;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

public class ProfileController {

    @FXML private Label usernameLabel, roleLabel;
    @FXML private PasswordField newPassField, confirmPassField;

    private User currentUser;

    public void setCurrentUser(User user) {
        this.currentUser = user;
        usernameLabel.setText(user.getUsername());
        roleLabel.setText(user.getRole().toUpperCase());
    }

    @FXML
    private void handleUpdatePassword() {
        String newPass = newPassField.getText();
        String confirm = confirmPassField.getText();

        if (newPass.isEmpty() || confirm.isEmpty()) {
            ModernDialog.showMessage("Action Required", "Please fill out both password fields.", true);
            return;
        }

        if (newPass.length() < 6) {
            ModernDialog.showMessage("Security Error", "Password must be at least 6 characters.", true);
            return;
        }

        if (!newPass.equals(confirm)) {
            ModernDialog.showMessage("Validation Error", "Passwords do not match.", true);
            return;
        }

        DatabaseManager.getInstance().updateUserPassword(currentUser.getUsername(), newPass);

        newPassField.clear();
        confirmPassField.clear();
        ModernDialog.showMessage("Success", "Your password has been successfully updated.", false);
    }
}