package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.util.HybridGeocoder;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;
import java.util.ResourceBundle;

public class ReportIncidentController implements Initializable {

    @FXML private ComboBox<String> incidentTypeCombo, severityCombo;
    @FXML private TextField locationField, latitudeField, longitudeField;
    @FXML private DatePicker incidentDatePicker;
    @FXML private TextArea descriptionArea;
    @FXML private Label validationLabel, savingLabel, geocodingLabel, fileNameLabel;
    @FXML private WebView reportMapWebView;
    @FXML private ImageView imagePreview;

    private String currentUsername = "User";
    private MainController mainController;
    private WebEngine engine;
    private final HybridGeocoder geocoder = new HybridGeocoder();

    // Holds the compressed Base64 string to save to DB
    private String base64Image = "";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        incidentTypeCombo.setItems(FXCollections.observableArrayList("Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"));
        severityCombo.setItems(FXCollections.observableArrayList("Low", "Medium", "High", "Critical"));
        incidentDatePicker.setValue(LocalDate.now());

        latitudeField.setEditable(false); longitudeField.setEditable(false);
        latitudeField.setPromptText("Click map to pin"); longitudeField.setPromptText("Click map to pin");

        hideLabel(validationLabel); hideLabel(savingLabel);
        if (geocodingLabel != null) hideLabel(geocodingLabel);

        engine = reportMapWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 DisasterReportApp/1.0");

        engine.setOnAlert(event -> {
            String data = event.getData();
            if (data != null) {
                if (data.startsWith("PIN_LOCATION:")) {
                    String[] coords = data.substring(13).split(",");
                    Platform.runLater(() -> {
                        latitudeField.setText(String.format(Locale.US, "%.6f", Double.parseDouble(coords[0])));
                        longitudeField.setText(String.format(Locale.US, "%.6f", Double.parseDouble(coords[1])));
                        if (geocodingLabel != null) { geocodingLabel.setText("Fetching address..."); geocodingLabel.setVisible(true); geocodingLabel.setManaged(true); }
                    });
                } else if (data.startsWith("ADDRESS_SUCCESS:")) {
                    String address = data.substring(16);
                    Platform.runLater(() -> { if (geocodingLabel != null) hideLabel(geocodingLabel); locationField.setText(address); });
                } else if (data.startsWith("ADDRESS_FAIL:")) {
                    String[] coords = data.substring(13).split(",");
                    geocoder.getAddress(Double.parseDouble(coords[0]), Double.parseDouble(coords[1])).thenAccept(address -> {
                        Platform.runLater(() -> { if (geocodingLabel != null) hideLabel(geocodingLabel); locationField.setText(address); });
                    });
                }
            }
        });
        try { loadMapPage(); } catch (Exception e) { e.printStackTrace(); }
    }

    public void setMainController(MainController mc) { this.mainController = mc; }
    public void setCurrentUsername(String username) { this.currentUsername = username; }

    // ── NEW: Image Upload & Compression Logic ──
    @FXML private void handleAttachPhoto() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Incident Photo");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg"));
        File file = fileChooser.showOpenDialog(reportMapWebView.getScene().getWindow());

        if (file != null) {
            fileNameLabel.setText("Processing image...");
            try {
                base64Image = compressAndEncodeImage(file);

                if (base64Image == null || base64Image.isEmpty()) {
                    fileNameLabel.setText("Error: Unreadable image format.");
                } else {
                    fileNameLabel.setText(file.getName() + " (Ready)");
                    imagePreview.setImage(new javafx.scene.image.Image(file.toURI().toString()));
                    imagePreview.setVisible(true);
                    imagePreview.setManaged(true);
                }
            } catch (Exception e) {
                // NEW: Smarter error handling for disguised files!
                if (e.getMessage() != null && e.getMessage().equals("Unsupported image format.")) {
                    fileNameLabel.setText("Error: Not a real JPG/PNG (Might be WebP/HEIC).");
                } else {
                    fileNameLabel.setText("Error: Could not process image.");
                }
                System.err.println(e.getMessage()); // Keeps the console clean
            }
        }
    }

    private String compressAndEncodeImage(File file) throws Exception {
        // 1. Use standard ImageIO (It is much more forgiving with weird file types)
        BufferedImage originalImage = ImageIO.read(file);

        // If ImageIO returns null, the file is physically not a valid image format
        if (originalImage == null) {
            throw new Exception("Unsupported image format.");
        }

        int origWidth = originalImage.getWidth();
        int origHeight = originalImage.getHeight();
        if (origWidth == 0 || origHeight == 0) return "";

        // 2. Resize math (Max width 600px to keep MySQL lightning fast)
        int targetWidth = 600;
        if (origWidth <= targetWidth) targetWidth = origWidth;
        int targetHeight = (int) (origHeight * ((double) targetWidth / origWidth));

        // 3. Create a clean white canvas (This instantly fixes the PNG transparency bug!)
        BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, targetWidth, targetHeight);

        // 4. Draw the original image onto the white canvas (shrinks it automatically)
        g.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
        g.dispose();

        // 5. Safely compress to Base64 String as a JPG!
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "jpg", baos);

        byte[] imageBytes = baos.toByteArray();
        if (imageBytes.length == 0) return "";

        return java.util.Base64.getEncoder().encodeToString(imageBytes);
    }

    // ── Map Loading ──
    private void loadMapPage() throws IOException {
        Path tempDir = Files.createTempDirectory("reportmap_"); tempDir.toFile().deleteOnExit();
        copyResource("/com/example/disasterreport/leaflet/leaflet.css", tempDir.resolve("leaflet.css"));
        copyResource("/com/example/disasterreport/leaflet/leaflet.js", tempDir.resolve("leaflet.js"));
        Files.writeString(tempDir.resolve("map.html"), buildMapHtml());
        engine.load(tempDir.resolve("map.html").toUri().toString());
    }

    private void copyResource(String cp, Path dest) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(cp)) { if (is != null) Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING); }
    }

    private String buildMapHtml() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'/><link rel='stylesheet' href='leaflet.css'/><style>html, body, #map { width: 100%; height: 100%; margin: 0; padding: 0; background: #e5e7eb; }</style></head><body><div id='map'></div><script src='leaflet.js'></script><script>L.Browser.any3d = false; var map = L.map('map').setView([12.8797, 121.7740], 6); var onlineUrl = 'https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png'; var offlineUrl = 'http://127.0.0.1:8080/tiles/{z}/{x}/{y}.png'; var tileLayer = L.tileLayer(onlineUrl, {maxZoom: 18}); tileLayer.on('tileerror', function(e) { var fallback = offlineUrl.replace('{z}', e.coords.z).replace('{x}', e.coords.x).replace('{y}', e.coords.y); if (e.tile.src !== fallback) { e.tile.src = fallback; } }); tileLayer.addTo(map); window.addEventListener('load', function() { setTimeout(function(){ map.invalidateSize(); }, 250); }); window.addEventListener('resize', function(){ map.invalidateSize(); }); var reportMarker = null; var svgIcon = '<svg width=\"24\" height=\"36\" viewBox=\"0 0 24 36\" xmlns=\"http://www.w3.org/2000/svg\"><path d=\"M12 0C5.373 0 0 5.373 0 12c0 8.25 12 24 12 24s12-15.75 12-24C24 5.373 18.627 0 12 0zm0 17.5c-3.038 0-5.5-2.462-5.5-5.5S8.962 6.5 12 6.5s5.5 2.462 5.5 5.5-2.462 5.5-5.5 5.5z\" fill=\"#e8550a\" stroke=\"#fff\" stroke-width=\"2\"/></svg>'; var customIcon = L.divIcon({className: '', html: svgIcon, iconSize: [24,36], iconAnchor: [12,36]}); function fetchAddress(lat, lng) { fetch('https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=' + lat + '&lon=' + lng, { headers: { 'Accept-Language': 'en-US,en;q=0.9' } }).then(function(res) { return res.json(); }).then(function(data) { if(data && data.display_name) { alert('ADDRESS_SUCCESS:' + data.display_name); } else { alert('ADDRESS_FAIL:' + lat + ',' + lng); } }).catch(function(err) { alert('ADDRESS_FAIL:' + lat + ',' + lng); }); } function placeMarker(lat, lng) { if (reportMarker) map.removeLayer(reportMarker); reportMarker = L.marker([lat, lng], {icon: customIcon, draggable: true}).addTo(map); reportMarker.on('dragend', function(e) { var pos = e.target.getLatLng(); alert('PIN_LOCATION:' + pos.lat + ',' + pos.lng); fetchAddress(pos.lat, pos.lng); }); } map.on('click', function(e) { placeMarker(e.latlng.lat, e.latlng.lng); alert('PIN_LOCATION:' + e.latlng.lat + ',' + e.latlng.lng); fetchAddress(e.latlng.lat, e.latlng.lng); });</script></body></html>";
    }

    @FXML private void handleSubmit() {
        if (incidentTypeCombo.getValue() == null || locationField.getText().trim().isEmpty() || incidentDatePicker.getValue() == null) {
            showLabel(validationLabel, "Please fill all required fields (*)."); return;
        }

        double lat = 0, lng = 0;
        try {
            if (!latitudeField.getText().isEmpty()) lat = Double.parseDouble(latitudeField.getText());
            if (!longitudeField.getText().isEmpty()) lng = Double.parseDouble(longitudeField.getText());
        } catch (Exception e) { showLabel(validationLabel, "Coordinates must be valid numbers."); return; }

        hideLabel(validationLabel); showLabel(savingLabel, "Saving…");

        // Pass base64Image into the new constructor
        Incident inc = new Incident(0, incidentTypeCombo.getValue(), locationField.getText().trim(), descriptionArea.getText().trim(), incidentDatePicker.getValue(), "Active", severityCombo.getValue() != null ? severityCombo.getValue() : "Medium", currentUsername, lat, lng, base64Image);

        inc.report();
        hideLabel(savingLabel); handleClear();
        if (mainController != null) mainController.refreshDashboard();

        // FIX: Replaced the ugly Alert with ModernDialog!
        com.example.disasterreport.util.ModernDialog.showMessage(
                "Success",
                "Incident reported successfully!",
                false
        );
    }

    @FXML private void handleClear() {
        incidentTypeCombo.getSelectionModel().clearSelection(); severityCombo.getSelectionModel().clearSelection();
        locationField.clear(); descriptionArea.clear(); latitudeField.clear(); longitudeField.clear();
        incidentDatePicker.setValue(LocalDate.now()); hideLabel(validationLabel); hideLabel(savingLabel);

        // Clear Image state
        base64Image = "";
        fileNameLabel.setText("No file selected");
        imagePreview.setVisible(false); imagePreview.setManaged(false);
        imagePreview.setImage(null);

        engine.executeScript("if(reportMarker) map.removeLayer(reportMarker);");
    }

    private void showLabel(Label l, String t) { l.setText(t); l.setVisible(true); l.setManaged(true); }
    private void hideLabel(Label l) { l.setVisible(false); l.setManaged(false); }
}