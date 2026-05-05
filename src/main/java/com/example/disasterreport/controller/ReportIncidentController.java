package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.net.URL;
import java.time.LocalDate;
import java.util.ResourceBundle;

/**
 * ReportIncidentController — handles the "Report Incident" form.
 *
 * Fixes applied
 * ─────────────
 * 1. Reads latitudeField / longitudeField from the FXML and passes the
 *    parsed values into the Incident constructor so markers appear on the map.
 * 2. Holds a reference to MainController and calls refreshDashboard() after
 *    every successful save so the dashboard stats update immediately.
 * 3. All UI updates remain on the JavaFX Application Thread (they already
 *    were — no Platform.runLater() needed here because handleSubmit() is
 *    called by a button click, which is already on that thread).
 */
public class ReportIncidentController implements Initializable {

    // ── FXML bindings ──────────────────────────────────────────────────────
    @FXML private ComboBox<String> incidentTypeCombo;
    @FXML private ComboBox<String> severityCombo;
    @FXML private TextField        locationField;
    @FXML private DatePicker       incidentDatePicker;
    @FXML private TextField        latitudeField;   // FIX 1 — was never read before
    @FXML private TextField        longitudeField;  // FIX 1
    @FXML private TextArea         descriptionArea;
    @FXML private Label            validationLabel;
    @FXML private Label            savingLabel;

    // ── State ──────────────────────────────────────────────────────────────
    private String         currentUsername  = "User";
    private MainController mainController;             // FIX 2 — dashboard refresh

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        incidentTypeCombo.setItems(FXCollections.observableArrayList(
                "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        severityCombo.setItems(FXCollections.observableArrayList(
                "Low", "Medium", "High", "Critical"
        ));
        incidentDatePicker.setValue(LocalDate.now());

        hideLabel(validationLabel);
        hideLabel(savingLabel);
    }

    // ── Setters called by MainController ──────────────────────────────────

    /** Called by MainController so this controller can refresh the dashboard. */
    public void setMainController(MainController mc) {
        this.mainController = mc;
    }

    /** Called by MainController to stamp the report with the logged-in user. */
    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    // ── FXML handlers ──────────────────────────────────────────────────────

    @FXML
    private void handleSubmit() {

        // ── 1. Validate required fields ────────────────────────────────────
        if (incidentTypeCombo.getValue() == null
                || locationField.getText().trim().isEmpty()
                || incidentDatePicker.getValue() == null) {

            showLabel(validationLabel, "Please fill all required fields (*).");
            return;
        }

        // ── 2. Parse optional coordinates (FIX 1) ─────────────────────────
        double lat = 0.0;
        double lng = 0.0;

        String latText = latitudeField  != null ? latitudeField.getText().trim()  : "";
        String lngText = longitudeField != null ? longitudeField.getText().trim() : "";

        if (!latText.isEmpty()) {
            try {
                lat = Double.parseDouble(latText);
            } catch (NumberFormatException e) {
                showLabel(validationLabel, "Latitude must be a valid number (e.g. 10.3157).");
                return;
            }
        }

        if (!lngText.isEmpty()) {
            try {
                lng = Double.parseDouble(lngText);
            } catch (NumberFormatException e) {
                showLabel(validationLabel, "Longitude must be a valid number (e.g. 123.8854).");
                return;
            }
        }

        // ── 3. Build and save the Incident ─────────────────────────────────
        hideLabel(validationLabel);
        showLabel(savingLabel, "Saving…");

        Incident incident = new Incident(
                0,
                incidentTypeCombo.getValue(),
                locationField.getText().trim(),
                descriptionArea.getText().trim(),
                incidentDatePicker.getValue(),
                "Active",
                severityCombo.getValue() != null ? severityCombo.getValue() : "Medium",
                currentUsername,
                lat,   // FIX 1 — now passes real coordinates
                lng
        );

        incident.report();           // → DatabaseManager.saveIncident()
        hideLabel(savingLabel);
        handleClear();

        // ── 4. Refresh dashboard immediately (FIX 2) ──────────────────────
        if (mainController != null) {
            mainController.refreshDashboard();
        }

        // ── 5. Success dialog ──────────────────────────────────────────────
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
        if (latitudeField  != null) latitudeField.clear();
        if (longitudeField != null) longitudeField.clear();
        incidentDatePicker.setValue(LocalDate.now());
        hideLabel(validationLabel);
        hideLabel(savingLabel);
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private void showLabel(Label label, String text) {
        if (text != null) label.setText(text);
        label.setVisible(true);
        label.setManaged(true);
    }

    private void hideLabel(Label label) {
        label.setVisible(false);
        label.setManaged(false);
    }
}