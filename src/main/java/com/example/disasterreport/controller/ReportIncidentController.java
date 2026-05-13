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
import netscape.javascript.JSObject;

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

    // Instantiate the JS bridge so it is preserved in memory and never Garbage Collected
    private final JSBridge jsBridge = new JSBridge();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        incidentTypeCombo.setItems(FXCollections.observableArrayList("Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"));
        severityCombo.setItems(FXCollections.observableArrayList("Low", "Medium", "High", "Critical"));
        incidentDatePicker.setValue(LocalDate.now());

        hideLabel(validationLabel); hideLabel(savingLabel);
        if (geocodingLabel != null) hideLabel(geocodingLabel);

        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        engine = reportMapWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        engine.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaApp", jsBridge);
            }
        });

        try { loadMapPage(); } catch (Exception e) { e.printStackTrace(); }
    }

    public void setMainController(MainController mc) { this.mainController = mc; }
    public void setCurrentUsername(String username) { this.currentUsername = username; }

    // ── 1. The Java-to-JavaScript Bridge ──────────────────────────────────

    public class JSBridge {
        public void reportLocation(String latStr, String lngStr) {
            Platform.runLater(() -> {
                try {
                    double lat = Double.parseDouble(latStr);
                    double lng = Double.parseDouble(lngStr);

                    latitudeField.setText(String.format(Locale.US, "%.6f", lat));
                    longitudeField.setText(String.format(Locale.US, "%.6f", lng));

                    reverseGeocode(lat, lng);
                } catch (Exception e) {
                    System.err.println("Failed to parse coordinates from map.");
                }
            });
        }
    }

    // ── 2. Async Reverse Geocoding (Nominatim) ────────────────────────────

    private void reverseGeocode(double lat, double lng) {
        if (geocodingLabel != null) { geocodingLabel.setVisible(true); geocodingLabel.setManaged(true); }
        String url = String.format(Locale.US, "https://nominatim.openstreetmap.org/reverse?format=json&lat=%f&lon=%f", lat, lng);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
                .header("User-Agent", "DisasterReportSystem/1.0").build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenAccept(body -> {
                    String address = extractJsonValue(body, "\"display_name\":\"");
                    Platform.runLater(() -> {
                        if (geocodingLabel != null) geocodingLabel.setVisible(false);
                        if (address != null && !address.isEmpty()) locationField.setText(address);
                    });
                }).exceptionally(ex -> {
                    Platform.runLater(() -> { if (geocodingLabel != null) geocodingLabel.setVisible(false); });
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

    // ── 3. HTML Generator & Local Extraction ───────────────────────────────

    private void loadMapPage() throws IOException {
        Path tempDir = Files.createTempDirectory("reportmap_");
        tempDir.toFile().deleteOnExit();

        copyResource("/com/example/disasterreport/leaflet/leaflet.css", tempDir.resolve("leaflet.css"));
        copyResource("/com/example/disasterreport/leaflet/leaflet.js", tempDir.resolve("leaflet.js"));

        Files.writeString(tempDir.resolve("map.html"), buildMapHtml());
        engine.load(tempDir.resolve("map.html").toUri().toString());
    }

    private void copyResource(String cp, Path dest) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(cp)) {
            if (is != null) Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String buildMapHtml() {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html><head><meta charset='utf-8'/>\n");
        h.append("<link rel='stylesheet' href='leaflet.css'/>\n");
        h.append("<style>html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #e5e7eb; }</style>\n");
        h.append("</head><body><div id='map'></div>\n");
        h.append("<script src='leaflet.js'></script>\n");
        h.append("<script>\n");
        h.append("    L.Browser.any3d = false;\n");
        h.append("    var map = L.map('map').setView([12.8797, 121.7740], 6);\n");
        h.append("    L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {maxZoom: 19}).addTo(map);\n");
        h.append("    \n");
        // FIX: Invalidating the size resolves the choppy/grey tiles issue
        h.append("    window.addEventListener('load', function() { setTimeout(function(){ map.invalidateSize(); }, 250); });\n");
        h.append("    window.addEventListener('resize', function(){ map.invalidateSize(); });\n");
        h.append("    \n");
        h.append("    var reportMarker = null;\n");
        h.append("    function placeMarker(lat, lng) {\n");
        h.append("        if (reportMarker) map.removeLayer(reportMarker);\n");
        h.append("        var iconHtml = '<div style=\"width:18px;height:18px;border-radius:50%;background:#e8550a;border:2px solid #fff;box-shadow:0 2px 5px rgba(0,0,0,0.4);\"></div>';\n");
        h.append("        var customIcon = L.divIcon({className: '', html: iconHtml, iconSize: [18,18], iconAnchor: [9,9]});\n");
        h.append("        reportMarker = L.marker([lat, lng], {icon: customIcon, draggable: true}).addTo(map);\n");
        h.append("        reportMarker.on('dragend', function(e) {\n");
        h.append("            var pos = e.target.getLatLng();\n");
        h.append("            if(window.javaApp) javaApp.reportLocation(pos.lat.toString(), pos.lng.toString());\n");
        h.append("        });\n");
        h.append("    }\n");
        h.append("    \n");
        h.append("    map.on('click', function(e) {\n");
        h.append("        placeMarker(e.latlng.lat, e.latlng.lng);\n");
        h.append("        if(window.javaApp) javaApp.reportLocation(e.latlng.lat.toString(), e.latlng.lng.toString());\n");
        h.append("    });\n");
        h.append("</script></body></html>");
        return h.toString();
    }

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
        if (mainController != null) mainController.refreshDashboard();

        Alert alert = new Alert(Alert.AlertType.INFORMATION, "Incident reported successfully!");
        alert.setHeaderText(null); alert.showAndWait();
    }

    @FXML private void handleClear() {
        incidentTypeCombo.getSelectionModel().clearSelection(); severityCombo.getSelectionModel().clearSelection();
        locationField.clear(); descriptionArea.clear(); latitudeField.clear(); longitudeField.clear();
        incidentDatePicker.setValue(LocalDate.now()); hideLabel(validationLabel); hideLabel(savingLabel);
        engine.executeScript("if(reportMarker) map.removeLayer(reportMarker);"); // Clear map pin
    }

    private void showLabel(Label l, String t) { l.setText(t); l.setVisible(true); l.setManaged(true); }
    private void hideLabel(Label l) { l.setVisible(false); l.setManaged(false); }
}