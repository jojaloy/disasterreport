package com.example.disasterreport.controller;

import com.example.disasterreport.model.Request;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import com.example.disasterreport.util.ModernDialog;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

public class ManageUsersController implements Initializable {

    // Tab Controls
    @FXML
    private Button tabUsers, tabRequests;
    @FXML
    private VBox usersContainer, requestsContainer;

    // Users Table
    @FXML
    private TableView<User> userTable;
    @FXML
    private TableColumn<User, Integer> idCol;
    @FXML
    private TableColumn<User, String> usernameCol;
    @FXML
    private TableColumn<User, String> roleCol; // Maps to roleName now!
    @FXML
    private Label userCountLabel;

    // Requests Table
    @FXML
    private TableView<Request> requestTable;
    @FXML
    private TableColumn<Request, Integer> reqIdCol;
    @FXML
    private TableColumn<Request, String> reqUsernameCol;
    @FXML
    private TableColumn<Request, String> reqTypeCol;
    @FXML
    private TableColumn<Request, String> reqDetailsCol;
    @FXML
    private TableColumn<Request, String> reqStatusCol;
    @FXML
    private Label requestCountLabel;

    // Edit User Overlay
    @FXML
    private StackPane editUserOverlay;
    @FXML
    private TextField editUsernameField, editPasswordField;
    @FXML
    private ComboBox<String> editRoleCombo;

    private MainController mainController;
    private User currentEditUser = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Init Users Table
        idCol.setCellValueFactory(new PropertyValueFactory<>("userID"));
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));

        // OOP UPDATE: Use "roleName" instead of "role" to match the User abstract class
        roleCol.setCellValueFactory(new PropertyValueFactory<>("roleName"));

        roleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String roleName, boolean empty) {
                super.updateItem(roleName, empty);
                if (empty || roleName == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                String color = switch (roleName) {
                    case "admin" -> "#dc2626";
                    case "responder" -> "#d97706";
                    default -> "#16a34a";
                };
                String icon = switch (roleName) {
                    case "admin" -> "🔑";
                    case "responder" -> "🚒";
                    default -> "📝";
                };
                setText(icon + "  " + roleName.substring(0, 1).toUpperCase() + roleName.substring(1));
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
            }
        });

        editRoleCombo.setItems(FXCollections.observableArrayList("reporter", "responder", "admin"));

        // Init Requests Table
        reqIdCol.setCellValueFactory(new PropertyValueFactory<>("requestID"));
        reqUsernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        reqTypeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        reqDetailsCol.setCellValueFactory(new PropertyValueFactory<>("details"));
        reqStatusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        reqStatusCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                setText(status);
                setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
            }
        });

        loadData();
    }

    public void setMainController(MainController mc) {
        this.mainController = mc;
    }

    private void loadData() {
        ObservableList<User> users = FXCollections.observableArrayList(DatabaseManager.getInstance().getAllUsers());
        userTable.setItems(users);
        userCountLabel.setText(users.size() + " users");

        ObservableList<Request> reqs = FXCollections.observableArrayList(DatabaseManager.getInstance().getPendingRequests());
        requestTable.setItems(reqs);
        requestCountLabel.setText(reqs.size() + " pending requests");

        tabRequests.setText("Pending Requests (" + reqs.size() + ")");
        if (mainController != null) mainController.refreshSidebarStats();
    }

    // ── Tab Switching ──

    @FXML
    private void showUsersTab() {
        tabUsers.setStyle("-fx-background-color: #e8550a; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6; -fx-padding: 8 16;");
        tabRequests.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6; -fx-padding: 8 16;");
        usersContainer.setVisible(true);
        usersContainer.setManaged(true);
        requestsContainer.setVisible(false);
        requestsContainer.setManaged(false);
    }

    @FXML
    private void showRequestsTab() {
        tabRequests.setStyle("-fx-background-color: #e8550a; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6; -fx-padding: 8 16;");
        tabUsers.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #374151; -fx-font-weight: bold; -fx-cursor: hand; -fx-background-radius: 6; -fx-padding: 8 16;");
        requestsContainer.setVisible(true);
        requestsContainer.setManaged(true);
        usersContainer.setVisible(false);
        usersContainer.setManaged(false);
    }

    // ── User Management ──

    @FXML
    private void handleEditUser() {
        currentEditUser = userTable.getSelectionModel().getSelectedItem();
        if (currentEditUser == null) {
            ModernDialog.showMessage("Action Required", "Please select a user to edit.", true);
            return;
        }
        editUsernameField.setText(currentEditUser.getUsername());

        // OOP UPDATE: Use getRoleName()
        editRoleCombo.setValue(currentEditUser.getRoleName());

        editPasswordField.clear();
        editUserOverlay.setVisible(true);
        editUserOverlay.setManaged(true);
    }

    @FXML
    private void closeEditOverlay() {
        editUserOverlay.setVisible(false);
        editUserOverlay.setManaged(false);
        currentEditUser = null;
    }

    @FXML
    private void saveUserEdit() {
        if (currentEditUser == null) return;
        String newName = editUsernameField.getText().trim();
        String newRole = editRoleCombo.getValue();
        String newPass = editPasswordField.getText();

        if (newName.isEmpty() || newRole == null) {
            ModernDialog.showMessage("Validation Error", "Username and Role cannot be empty.", true);
            return;
        }

        boolean ok = DatabaseManager.getInstance().updateUserAdmin(currentEditUser.getUserID(), newName, newRole, newPass);
        if (ok) {
            ModernDialog.showMessage("Success", "User details updated successfully.", false);
            loadData();
            closeEditOverlay();
        } else {
            ModernDialog.showMessage("Error", "Failed to update user. Username may already exist.", true);
        }
    }

    @FXML
    private void handleDeleteUser() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            ModernDialog.showMessage("Action Required", "Please select a user to delete.", true);
            return;
        }

        Optional<String> choice = ModernDialog.showChoice("Confirm Deletion",
                "Are you sure you want to permanently delete '" + selected.getUsername() + "'?",
                new String[]{"No, Cancel", "Yes, Delete"});

        if (choice.isPresent() && choice.get().equals("Yes, Delete")) {
            if (DatabaseManager.getInstance().deleteUser(selected.getUserID())) {
                ModernDialog.showMessage("Deleted", "User has been removed from the system.", false);
                loadData();
            } else {
                ModernDialog.showMessage("Error", "Could not delete user.", true);
            }
        }
    }

    // ── Request Management ──

    @FXML
    private void handleApproveRequest() {
        Request req = requestTable.getSelectionModel().getSelectedItem();
        if (req == null) { ModernDialog.showMessage("Action Required", "Select a request to approve.", true); return; }

        if ("PASSWORD_RESET".equals(req.getType())) {
            Optional<String> result = ModernDialog.showTextInput("Approve Password Reset", "Set a temporary password for " + req.getUsername() + ":", "temp123");
            if (result.isPresent() && !result.get().trim().isEmpty()) {
                DatabaseManager.getInstance().updateUserPassword(req.getUsername(), result.get().trim());
                DatabaseManager.getInstance().updateRequestStatus(req.getRequestID(), "APPROVED");
                ModernDialog.showMessage("Success", "Password reset successfully! Make sure to inform the user.", false);
                loadData();
            }

        } else if ("ROLE_UPGRADE".equals(req.getType())) {

            // FIX: Convert the entire details string to lowercase so .contains() works perfectly!
            String fullDetails = req.getDetails().toLowerCase();
            String requestedRole = "reporter"; // Default fallback

            if (fullDetails.contains("admin")) {
                requestedRole = "admin";
            } else if (fullDetails.contains("responder")) {
                requestedRole = "responder";
            }

            DatabaseManager.getInstance().updateUserRoleByUsername(req.getUsername(), requestedRole);
            DatabaseManager.getInstance().updateRequestStatus(req.getRequestID(), "APPROVED");
            ModernDialog.showMessage("Success", "Role updated successfully!", false);
            loadData();
        }
    }
    @FXML
    private void handleRejectRequest() {
        Request req = requestTable.getSelectionModel().getSelectedItem();
        if (req == null) {
            ModernDialog.showMessage("Action Required", "Select a request to reject.", true);
            return;
        }

        DatabaseManager.getInstance().updateRequestStatus(req.getRequestID(), "REJECTED");
        ModernDialog.showMessage("Rejected", "Request has been rejected.", false);
        loadData();
    }
}