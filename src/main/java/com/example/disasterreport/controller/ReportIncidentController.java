package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

public class ReportIncidentController implements Initializable {

    @FXML private ComboBox<String> incidentTypeCombo;
    @FXML private ComboBox<String> severityCombo;
    @FXML private TextField locationField;
    @FXML private DatePicker incidentDatePicker;
    @FXML private TextArea descriptionArea;
    @FXML private Label validationLabel;
    @FXML private Label savingLabel;

    private String currentUsername = "User"; // will be set from MainController if needed

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        incidentTypeCombo.setItems(FXCollections.observableArrayList(
                "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        severityCombo.setItems(FXCollections.observableArrayList(
                "Low", "Medium", "High", "Critical"
        ));
        incidentDatePicker.setValue(LocalDate.now());
        validationLabel.setVisible(false);
        savingLabel.setVisible(false);
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    @FXML
    private void handleSubmit() {
        // Validate required fields
        if (incidentTypeCombo.getValue() == null ||
                locationField.getText().trim().isEmpty() ||
                incidentDatePicker.getValue() == null) {
            validationLabel.setText("Please fill all required fields (*).");
            validationLabel.setVisible(true);
            return;
        }

        validationLabel.setVisible(false);
        savingLabel.setVisible(true);

        Incident incident = new Incident(
                0,
                incidentTypeCombo.getValue(),
                locationField.getText().trim(),
                descriptionArea.getText().trim(),
                incidentDatePicker.getValue(),
                "Active",
                severityCombo.getValue() != null ? severityCombo.getValue() : "Medium",
                currentUsername
        );

        incident.report(); // calls DatabaseManager.saveIncident()
        savingLabel.setVisible(false);
        handleClear();

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Success");
        alert.setHeaderText(null);
        alert.setContentText("Incident reported successfully!");
        alert.showAndWait();
    }

    @FXML
    private void handleClear() {
        incidentTypeCombo.getSelectionModel().clearSelection();
        severityCombo.getSelectionModel().clearSelection();
        locationField.clear();
        descriptionArea.clear();
        incidentDatePicker.setValue(LocalDate.now());
        validationLabel.setVisible(false);
        savingLabel.setVisible(false);
    }
}