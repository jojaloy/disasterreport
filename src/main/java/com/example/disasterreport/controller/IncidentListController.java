package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.util.DatabaseManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class IncidentListController implements Initializable {

    @FXML private TableView<Incident>              incidentTable;
    @FXML private TableColumn<Incident, Integer>   idColumn;
    @FXML private TableColumn<Incident, String>    typeColumn;
    @FXML private TableColumn<Incident, String>    locationColumn;
    @FXML private TableColumn<Incident, String>    dateColumn;
    @FXML private TableColumn<Incident, String>    statusColumn;
    @FXML private TableColumn<Incident, String>    reportedByColumn;

    @FXML private TextField         searchField;
    @FXML private ComboBox<String>  typeFilterCombo;
    @FXML private ComboBox<String>  statusFilterCombo;
    @FXML private Label             recordCountLabel;
    @FXML private Button            viewDetailsButton;
    @FXML private Button            updateStatusButton;

    private ObservableList<Incident> allData;
    private MainController           mainController;
    private String                   currentUserRole = "reporter";

    public void setMainController(MainController mc)  { this.mainController = mc; }
    public void setCurrentUserRole(String role)        {
        this.currentUserRole = role;
        // Only admins and responders can update status
        boolean canUpdate = "admin".equals(role) || "responder".equals(role);
        updateStatusButton.setDisable(true);   // still needs a selection; enable on select
        updateStatusButton.setVisible(canUpdate);
        updateStatusButton.setManaged(canUpdate);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("incidentID"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        locationColumn.setCellValueFactory(new PropertyValueFactory<>("location"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        reportedByColumn.setCellValueFactory(new PropertyValueFactory<>("reportedBy"));

        dateColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getDate() != null
                                ? cell.getValue().getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                : ""
                )
        );

        // Color-code the Type column
        typeColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) { setText(null); setStyle(""); return; }
                setText(iconFor(type) + " " + type);
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + colorFor(type) + ";");
            }
        });

        // Color-code the Status column
        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(statusIcon(status) + " " + status);
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + statusColor(status) + ";");
            }
        });

        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Active", "Monitoring", "Resolved"
        ));

        viewDetailsButton.setDisable(true);
        updateStatusButton.setDisable(true);

        incidentTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, selected) -> {
                    boolean hasSelection = selected != null;
                    viewDetailsButton.setDisable(!hasSelection);
                    // Only enable update if role allows
                    boolean canUpdate = "admin".equals(currentUserRole) || "responder".equals(currentUserRole);
                    updateStatusButton.setDisable(!hasSelection || !canUpdate);
                }
        );

        loadData();
    }

    private void loadData() {
        allData = FXCollections.observableArrayList(DatabaseManager.getInstance().getIncidents());
        incidentTable.setItems(allData);
        recordCountLabel.setText(allData.size() + " incidents");
    }

    @FXML private void handleSearch()       { applyFilters(); }
    @FXML private void handleFilter()       { applyFilters(); }
    @FXML private void handleRefresh()      { loadData(); }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        typeFilterCombo.getSelectionModel().clearSelection();
        statusFilterCombo.getSelectionModel().clearSelection();
        incidentTable.setItems(allData);
        recordCountLabel.setText(allData.size() + " incidents");
    }

    private void applyFilters() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String type    = typeFilterCombo.getValue();
        String status  = statusFilterCombo.getValue();

        ObservableList<Incident> filtered = allData.filtered(i ->
                (keyword.isEmpty() ||
                        i.getLocation().toLowerCase().contains(keyword) ||
                        i.getType().toLowerCase().contains(keyword)) &&
                        (type   == null || type.equals("All")   || i.getType().equals(type)) &&
                        (status == null || status.equals("All") || i.getStatus().equals(status))
        );

        incidentTable.setItems(filtered);
        recordCountLabel.setText(filtered.size() + " incidents");
    }

    @FXML
    private void handleViewDetails() {
        Incident sel = incidentTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Incident Details");
        alert.setHeaderText(iconFor(sel.getType()) + "  Incident #" + sel.getIncidentID() + " — " + sel.getType());
        alert.setContentText(
                "Location   : " + sel.getLocation() + "\n" +
                        "Date       : " + sel.getDate() + "\n" +
                        "Status     : " + sel.getStatus() + "\n" +
                        "Severity   : " + sel.getSeverity() + "\n" +
                        "Reported By: " + sel.getReportedBy() + "\n\n" +
                        "Description:\n" + (sel.getDescription() != null ? sel.getDescription() : "—")
        );
        alert.showAndWait();
    }

    @FXML
    private void handleUpdateStatus() {
        // Role guard
        if (!"admin".equals(currentUserRole) && !"responder".equals(currentUserRole)) {
            new Alert(Alert.AlertType.WARNING, "You do not have permission to update statuses.").showAndWait();
            return;
        }

        Incident sel = incidentTable.getSelectionModel().getSelectedItem();
        if (sel == null) return;

        // Let user pick new status
        ChoiceDialog<String> dialog = new ChoiceDialog<>(sel.getStatus(),
                "Active", "Monitoring", "Resolved");
        dialog.setTitle("Update Status");
        dialog.setHeaderText("Change status for Incident #" + sel.getIncidentID());
        dialog.setContentText("New status:");

        dialog.showAndWait().ifPresent(newStatus -> {
            sel.setStatus(newStatus);
            DatabaseManager.getInstance().updateIncident(sel);
            incidentTable.refresh();
            if (mainController != null) mainController.refreshDashboard();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private String colorFor(String type) {
        if (type == null) return "#6b7280";
        return switch (type) {
            case "Flood"      -> "#3b82f6";
            case "Fire"       -> "#ef4444";
            case "Earthquake" -> "#f97316";
            case "Typhoon"    -> "#8b5cf6";
            case "Landslide"  -> "#78350f";
            default           -> "#6b7280";
        };
    }

    private String iconFor(String type) {
        if (type == null) return "⚠";
        return switch (type) {
            case "Flood"      -> "🌊";
            case "Fire"       -> "🔥";
            case "Earthquake" -> "⚡";
            case "Typhoon"    -> "🌀";
            case "Landslide"  -> "⛰";
            default           -> "⚠";
        };
    }

    private String statusIcon(String status) {
        if (status == null) return "●";
        return switch (status) {
            case "Active"     -> "🔴";
            case "Monitoring" -> "🟡";
            case "Resolved"   -> "🟢";
            default           -> "●";
        };
    }

    private String statusColor(String status) {
        if (status == null) return "#6b7280";
        return switch (status) {
            case "Active"     -> "#dc2626";
            case "Monitoring" -> "#d97706";
            case "Resolved"   -> "#16a34a";
            default           -> "#6b7280";
        };
    }
}