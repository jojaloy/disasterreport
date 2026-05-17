package com.example.disasterreport.controller;

import com.example.disasterreport.model.*;
import com.example.disasterreport.util.DatabaseManager;
import com.example.disasterreport.util.ModernDialog; // Added import for the custom dialog!
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.collections.FXCollections;

public class ProfileController {

    @FXML private Label lblUsername, lblRole, lblAgency, lblTrustScore;
    @FXML private HBox agencyContainer, trustContainer;
    @FXML private PasswordField txtNewPassword, txtConfirmPassword;
    @FXML private ComboBox<String> comboRequestedRole;
    @FXML private TextField txtReason;

    private User currentUser;

    @FXML
    public void initialize() {
        comboRequestedRole.setItems(FXCollections.observableArrayList("Responder", "Admin"));
    }

    public void setUserInfo(User user) {
        this.currentUser = user;
        lblUsername.setText(user.getUsername());
        lblRole.setText(user.getRoleName().toUpperCase());

        // OOP MAGIC: Check subclass type using instanceof
        if (user instanceof Responder responder) {
            lblAgency.setText(responder.getAgency());
            agencyContainer.setVisible(true);
            agencyContainer.setManaged(true);
        } else if (user instanceof Reporter reporter) {
            lblTrustScore.setText(reporter.getTrustScore() + " / 100");
            trustContainer.setVisible(true);
            trustContainer.setManaged(true);
        } else if (user instanceof Admin) {
            comboRequestedRole.setDisable(true);
            txtReason.setDisable(true);
        }
    }

    @FXML
    private void handleUpdatePassword() {
        String newPass = txtNewPassword.getText();
        String confirmPass = txtConfirmPassword.getText();

        if (newPass.isEmpty() || !newPass.equals(confirmPass)) {
            // Replaced with ModernDialog (true = isError)
            ModernDialog.showMessage("Error", "Passwords do not match or are empty.", true);
            return;
        }

        DatabaseManager.getInstance().updateUserPassword(currentUser.getUsername(), newPass);

        // Replaced with ModernDialog (false = success)
        ModernDialog.showMessage("Success", "Password updated successfully!", false);
        txtNewPassword.clear();
        txtConfirmPassword.clear();
    }

    @FXML
    private void handleSubmitRequest() {
        String requestedRole = comboRequestedRole.getValue();
        String reason = txtReason.getText();

        if (requestedRole == null) {
            ModernDialog.showMessage("Action Required", "Please select a role.", true);
            return;
        }

        boolean success = DatabaseManager.getInstance().addRequest(
                currentUser.getUsername(),
                "ROLE_UPGRADE",
                "Requested: " + requestedRole + " | Reason: " + reason
        );

        if (success) {
            ModernDialog.showMessage("Success", "Request submitted to the Admin team.", false);
            txtReason.clear();
            comboRequestedRole.setValue(null);
        } else {
            ModernDialog.showMessage("Error", "Failed to submit request.", true);
        }
    }
}