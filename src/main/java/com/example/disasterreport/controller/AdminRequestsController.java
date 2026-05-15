package com.example.disasterreport.controller;

import com.example.disasterreport.model.Request;
import com.example.disasterreport.util.DatabaseManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class AdminRequestsController implements Initializable {
    @FXML private TableView<Request> requestTable;
    @FXML private TableColumn<Request, Integer> idCol;
    @FXML private TableColumn<Request, String> usernameCol;
    @FXML private TableColumn<Request, String> typeCol;
    @FXML private TableColumn<Request, String> detailsCol;
    @FXML private TableColumn<Request, String> statusCol;
    @FXML private Label countLabel;

    private ObservableList<Request> pendingRequests;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        idCol.setCellValueFactory(new PropertyValueFactory<>("requestID"));
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        detailsCol.setCellValueFactory(new PropertyValueFactory<>("details"));
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));

        statusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
            }
        });

        loadData();
    }

    private void loadData() {
        pendingRequests = FXCollections.observableArrayList(DatabaseManager.getInstance().getPendingRequests());
        requestTable.setItems(pendingRequests);
        countLabel.setText(pendingRequests.size() + " pending requests");
    }

    @FXML private void handleRefresh() { loadData(); }

    @FXML
    private void handleApprove() {
        Request req = requestTable.getSelectionModel().getSelectedItem();
        if (req == null) {
            com.example.disasterreport.util.ModernDialog.showMessage("Action Required", "Select a request to approve.", true);
            return;
        }

        if ("PASSWORD_RESET".equals(req.getType())) {
            java.util.Optional<String> result = com.example.disasterreport.util.ModernDialog.showTextInput(
                    "Approve Password Reset",
                    "Set a temporary password for " + req.getUsername() + ":",
                    "temp123"
            );

            if (result.isPresent() && !result.get().trim().isEmpty()) {
                DatabaseManager.getInstance().updateUserPassword(req.getUsername(), result.get().trim());
                DatabaseManager.getInstance().updateRequestStatus(req.getRequestID(), "APPROVED");
                com.example.disasterreport.util.ModernDialog.showMessage("Success", "Password reset successfully! Make sure to inform the user.", false);
                loadData();
            }
        } else if ("ROLE_CHANGE".equals(req.getType())) {
            DatabaseManager.getInstance().updateUserRoleByUsername(req.getUsername(), req.getDetails());
            DatabaseManager.getInstance().updateRequestStatus(req.getRequestID(), "APPROVED");
            com.example.disasterreport.util.ModernDialog.showMessage("Success", "Role updated successfully!", false);
            loadData();
        }
    }

    @FXML
    private void handleReject() {
        Request req = requestTable.getSelectionModel().getSelectedItem();
        if (req == null) {
            com.example.disasterreport.util.ModernDialog.showMessage("Action Required", "Select a request to reject.", true);
            return;
        }
        DatabaseManager.getInstance().updateRequestStatus(req.getRequestID(), "REJECTED");
        com.example.disasterreport.util.ModernDialog.showMessage("Rejected", "Request has been rejected.", false);
        loadData();
    }
}