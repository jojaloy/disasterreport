package com.example.disasterreport.controller;

import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class ManageUsersController implements Initializable {

    @FXML private TableView<User>             userTable;
    @FXML private TableColumn<User, Integer>  idCol;
    @FXML private TableColumn<User, String>   usernameCol;
    @FXML private TableColumn<User, String>   roleCol;
    @FXML private Label                        countLabel;
    @FXML private ComboBox<String>             roleChangeCombo;

    private ObservableList<User> allUsers;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        idCol.setCellValueFactory(new PropertyValueFactory<>("userID"));
        usernameCol.setCellValueFactory(new PropertyValueFactory<>("username"));
        roleCol.setCellValueFactory(new PropertyValueFactory<>("role"));

        // Color-code the role column
        roleCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String role, boolean empty) {
                super.updateItem(role, empty);
                if (empty || role == null) { setText(null); setStyle(""); return; }
                String color = switch (role) {
                    case "admin"     -> "#dc2626";
                    case "responder" -> "#d97706";
                    default          -> "#16a34a";
                };
                String icon = switch (role) {
                    case "admin"     -> "🔑";
                    case "responder" -> "🚒";
                    default          -> "📝";
                };
                setText(icon + "  " + role.substring(0,1).toUpperCase() + role.substring(1));
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + ";");
            }
        });

        roleChangeCombo.setItems(FXCollections.observableArrayList("reporter", "responder", "admin"));

        loadData();
    }

    private void loadData() {
        allUsers = FXCollections.observableArrayList(DatabaseManager.getInstance().getAllUsers());
        userTable.setItems(allUsers);
        countLabel.setText(allUsers.size() + " users");
    }

    @FXML
    private void handleChangeRole() {
        User selected = userTable.getSelectionModel().getSelectedItem();
        String newRole = roleChangeCombo.getValue();

        if (selected == null) {
            new Alert(Alert.AlertType.WARNING, "Please select a user first.").showAndWait();
            return;
        }
        if (newRole == null) {
            new Alert(Alert.AlertType.WARNING, "Please select a role to assign.").showAndWait();
            return;
        }

        boolean ok = DatabaseManager.getInstance().updateUserRole(selected.getUserID(), newRole);
        if (ok) {
            new Alert(Alert.AlertType.INFORMATION,
                    "Role updated: " + selected.getUsername() + " → " + newRole).showAndWait();
            loadData();
        } else {
            new Alert(Alert.AlertType.ERROR, "Failed to update role.").showAndWait();
        }
    }

    @FXML
    private void handleRefresh() { loadData(); }
}