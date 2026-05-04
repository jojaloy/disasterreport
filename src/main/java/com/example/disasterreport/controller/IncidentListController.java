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

    @FXML private TableView<Incident> incidentTable;
    @FXML private TableColumn<Incident, Integer> idColumn;
    @FXML private TableColumn<Incident, String> typeColumn;
    @FXML private TableColumn<Incident, String> locationColumn;
    @FXML private TableColumn<Incident, String> dateColumn;
    @FXML private TableColumn<Incident, String> statusColumn;
    @FXML private TableColumn<Incident, String> reportedByColumn;

    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private ComboBox<String> statusFilterCombo;
    @FXML private Label recordCountLabel;

    @FXML private Button viewDetailsButton;
    @FXML private Button updateStatusButton;

    private ObservableList<Incident> allData;

    private MainController mainController;

    public void setMainController(MainController mainController) {
        this.mainController = mainController;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {

        idColumn.setCellValueFactory(new PropertyValueFactory<>("incidentID"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        locationColumn.setCellValueFactory(new PropertyValueFactory<>("location"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        reportedByColumn.setCellValueFactory(new PropertyValueFactory<>("reportedBy"));

        // IMPORTANT FIX: LocalDate needs formatting
        dateColumn.setCellValueFactory(cell ->
                new javafx.beans.property.SimpleStringProperty(
                        cell.getValue().getDate() != null
                                ? cell.getValue().getDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                : ""
                )
        );

        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));

        statusFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Active", "Monitoring", "Resolved"
        ));

        viewDetailsButton.setDisable(true);
        updateStatusButton.setDisable(true);

        incidentTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, selected) -> {
                    boolean disabled = selected == null;
                    viewDetailsButton.setDisable(disabled);
                    updateStatusButton.setDisable(disabled);
                }
        );

        loadData();
    }

    private void loadData() {
        allData = FXCollections.observableArrayList(
                DatabaseManager.getInstance().getIncidents()
        );

        System.out.println("INCIDENTS LOADED: " + allData.size());

        for (Incident i : allData) {
            System.out.println(i.getIncidentID() + " | " + i.getType());
        }

        incidentTable.setItems(allData);
        recordCountLabel.setText(allData.size() + " incidents");
    }

    @FXML private void handleSearch() { applyFilters(); }
    @FXML private void handleFilter() { applyFilters(); }
    @FXML private void handleRefresh() { loadData(); }

    @FXML
    private void handleClearFilters() {
        searchField.clear();
        typeFilterCombo.getSelectionModel().clearSelection();
        statusFilterCombo.getSelectionModel().clearSelection();
        incidentTable.setItems(allData);
    }

    private void applyFilters() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String type = typeFilterCombo.getValue();
        String status = statusFilterCombo.getValue();

        ObservableList<Incident> filtered = allData.filtered(i ->
                (keyword.isEmpty() ||
                        i.getLocation().toLowerCase().contains(keyword) ||
                        i.getType().toLowerCase().contains(keyword)
                ) &&
                        (type == null || type.equals("All") || i.getType().equals(type)) &&
                        (status == null || status.equals("All") || i.getStatus().equals(status))
        );

        incidentTable.setItems(filtered);
        recordCountLabel.setText(filtered.size() + " incidents");
    }

    @FXML
    private void handleViewDetails() {
        Incident selected = incidentTable.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Incident Details");
        alert.setHeaderText("Incident #" + selected.getIncidentID());
        alert.setContentText(
                "Type: " + selected.getType() + "\n" +
                        "Location: " + selected.getLocation() + "\n" +
                        "Date: " + selected.getDate() + "\n" +
                        "Status: " + selected.getStatus() + "\n" +
                        "Reported By: " + selected.getReportedBy() + "\n\n" +
                        "Description: " + selected.getDescription()
        );
        alert.showAndWait();
    }

    @FXML
    private void handleUpdateStatus() {

        Incident selected = incidentTable.getSelectionModel().getSelectedItem();

        if (selected == null) return;

        selected.setStatus("Resolved");

        DatabaseManager.getInstance().updateIncident(selected);

        incidentTable.refresh();

        // 🔥 IMPORTANT: update dashboard counters
        if (mainController != null) {
            mainController.refreshDashboard();
        }
    }


}