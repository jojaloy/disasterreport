package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.Duration;
import java.util.Locale;
import java.util.ResourceBundle;

public class ReportIncidentController implements Initializable {

    @FXML private ComboBox<String> incidentTypeCombo, severityCombo;
    @FXML private TextField locationField, latitudeField, longitudeField;
    @FXML private DatePicker incidentDatePicker;
    @FXML private TextArea descriptionArea;
    @FXML private Label validationLabel, savingLabel, geocodingLabel;
    @FXML private WebView reportMapWebView;

    private String currentUsername = "User";
    private MainController mainController;
    private WebEngine engine;
    private HttpClient httpClient;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initUI();
        initMap();
    }

    public void setMainController(MainController mc) { this.mainController = mc; }
    public void setCurrentUsername(String username) { this.currentUsername = username; }

    // ── 1. Initialization ─────────────────────────────────────────────────

    private void initUI() {
        incidentTypeCombo.setItems(FXCollections.observableArrayList("Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"));
        severityCombo.setItems(FXCollections.observableArrayList("Low", "Medium", "High", "Critical"));
        incidentDatePicker.setValue(LocalDate.now());

        hideLabel(validationLabel);
        hideLabel(savingLabel);
        if (geocodingLabel != null) hideLabel(geocodingLabel);

        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    private void initMap() {
        engine = reportMapWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

        // Bulletproof JS-to-Java bridge: Intercept hidden Javascript alerts
        engine.setOnAlert(event -> {
            String data = event.getData();
            if (data != null && data.startsWith("PIN_LOCATION:")) {
                handleMapPin(data.substring(13));
            }
        });

        try { loadMapPage(); }
        catch (Exception e) { System.err.println("Failed to load map: " + e.getMessage()); }
    }

    // ── 2. Bridging & Geocoding Logic ─────────────────────────────────────

    private void handleMapPin(String coordinateData) {
        String[] coords = coordinateData.split(",");
        if (coords.length == 2) {
            Platform.runLater(() -> {
                try {
                    double lat = Double.parseDouble(coords[0]);
                    double lng = Double.parseDouble(coords[1]);

                    latitudeField.setText(String.format(Locale.US, "%.6f", lat));
                    longitudeField.setText(String.format(Locale.US, "%.6f", lng));

                    reverseGeocode(lat, lng);
                } catch (NumberFormatException e) {
                    System.err.println("Invalid coordinates received from map.");
                }
            });
        }
    }

    private void reverseGeocode(double lat, double lng) {
        if (geocodingLabel != null) {
            geocodingLabel.setText("Fetching address...");
            geocodingLabel.setVisible(true);
            geocodingLabel.setManaged(true);
        }

        String url = String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=json&lat=%f&lon=%f", lat, lng);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("User-Agent", "DisasterReportSystem/1.0").build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    String address = extractJsonValue(body, "\"display_name\":\"");
                    Platform.runLater(() -> {
                        if (geocodingLabel != null) hideLabel(geocodingLabel);
                        if (address != null && !address.isEmpty()) locationField.setText(address);
                    });
                }).exceptionally(ex -> {
                    Platform.runLater(() -> { if (geocodingLabel != null) hideLabel(geocodingLabel); });
                    return null;
                });
    }

    private String extractJsonValue(String json, String key) {
        int idx = json.indexOf(key);
        if (idx == -1) return null;
        int start = idx + key.length();
        int end = json.indexOf("\"", start);
        return json.substring(start, end).replace("\\\"", "\"");
    }

    // ── 3. HTML Rendering ──────────────────────────────────────────────────

    private void loadMapPage() throws IOException {
        Path tempDir = Files.createTempDirectory("reportmap_");
        tempDir.toFile().deleteOnExit();
        copyResource("/com/example/disasterreport/leaflet/leaflet.css", tempDir.resolve("leaflet.css"));
        copyResource("/com/example/disasterreport/leaflet/leaflet.js", tempDir.resolve("leaflet.js"));
        Files.writeString(tempDir.resolve("map.html"), getMapHtml());
        engine.load(tempDir.resolve("map.html").toUri().toString());
    }

    private void copyResource(String cp, Path dest) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(cp)) {
            if (is != null) Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String getMapHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset='utf-8'/>
                <link rel='stylesheet' href='leaflet.css'/>
                <style>html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #e5e7eb; }</style>
            </head>
            <body>
                <div id='map'></div>
                <script src='leaflet.js'></script>
                <script>
                    L.Browser.any3d = false;
                    var map = L.map('map').setView([12.8797, 121.7740], 6);
                    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {maxZoom: 19}).addTo(map);
                    
                    window.addEventListener('load', function() { setTimeout(function(){ map.invalidateSize(); }, 250); });
                    window.addEventListener('resize', function(){ map.invalidateSize(); });
                    
                    var reportMarker = null;
                    var svgIcon = '<svg width="24" height="36" viewBox="0 0 24 36" xmlns="http://www.w3.org/2000/svg"><path d="M12 0C5.373 0 0 5.373 0 12c0 8.25 12 24 12 24s12-15.75 12-24C24 5.373 18.627 0 12 0zm0 17.5c-3.038 0-5.5-2.462-5.5-5.5S8.962 6.5 12 6.5s5.5 2.462 5.5 5.5-2.462 5.5-5.5 5.5z" fill="#e8550a" stroke="#fff" stroke-width="2"/></svg>';
                    var customIcon = L.divIcon({className: '', html: svgIcon, iconSize: [24,36], iconAnchor: [12,36]});
                    
                    function placeMarker(lat, lng) {
                        if (reportMarker) map.removeLayer(reportMarker);
                        reportMarker = L.marker([lat, lng], {icon: customIcon, draggable: true}).addTo(map);
                        reportMarker.on('dragend', function(e) {
                            var pos = e.target.getLatLng();
                            alert('PIN_LOCATION:' + pos.lat + ',' + pos.lng);
                        });
                    }
                    
                    map.on('click', function(e) {
                        placeMarker(e.latlng.lat, e.latlng.lng);
                        alert('PIN_LOCATION:' + e.latlng.lat + ',' + e.latlng.lng);
                    });
                </script>
            </body>
            </html>
            """;
    }

    // ── 4. Form Actions ────────────────────────────────────────────────────

    @FXML private void handleSubmit() {
        if (incidentTypeCombo.getValue() == null || locationField.getText().trim().isEmpty() || incidentDatePicker.getValue() == null) {
            showLabel(validationLabel, "Please fill all required fields (*)."); return;
        }

        double lat = 0, lng = 0;
        try {
            if (!latitudeField.getText().isEmpty()) lat = Double.parseDouble(latitudeField.getText());
            if (!longitudeField.getText().isEmpty()) lng = Double.parseDouble(longitudeField.getText());
        } catch (Exception e) {
            showLabel(validationLabel, "Coordinates must be valid numbers."); return;
        }

        hideLabel(validationLabel); showLabel(savingLabel, "Saving…");
        Incident inc = new Incident(0, incidentTypeCombo.getValue(), locationField.getText().trim(), descriptionArea.getText().trim(), incidentDatePicker.getValue(), "Active", severityCombo.getValue() != null ? severityCombo.getValue() : "Medium", currentUsername, lat, lng);

        inc.report();
        hideLabel(savingLabel); handleClear();
        if (mainController != null) mainController.refreshSidebarStats();

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Incident reported successfully!");
        alert.setHeaderText(null); alert.showAndWait();
    }

    @FXML private void handleClear() {
        incidentTypeCombo.getSelectionModel().clearSelection(); severityCombo.getSelectionModel().clearSelection();
        locationField.clear(); descriptionArea.clear(); latitudeField.clear(); longitudeField.clear();
        incidentDatePicker.setValue(LocalDate.now()); hideLabel(validationLabel); hideLabel(savingLabel);
        engine.executeScript("if(reportMarker) map.removeLayer(reportMarker);");
    }

    private void showLabel(Label l, String t) { l.setText(t); l.setVisible(true); l.setManaged(true); }
    private void hideLabel(Label l) { l.setVisible(false); l.setManaged(false); }
}