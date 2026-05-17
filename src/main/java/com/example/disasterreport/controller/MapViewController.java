package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.ResourceBundle;

public class MapViewController implements Initializable {

    @FXML private TableView<Incident> incidentTable;
    @FXML private TableColumn<Incident, Integer> idColumn;
    @FXML private TableColumn<Incident, String> typeColumn, locationColumn, severityColumn, statusColumn;
    @FXML private WebView mapWebView;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterCombo;

    // UI Panels & Toggles
    @FXML private VBox listPanel, detailsPanel;
    @FXML private Button btnShowList, btnShowDetails, btnUpdateStatus, btnViewPhoto;
    private boolean isListVisible = true;
    private boolean isDetailsCollapsed = false;

    @FXML private Label markerCountLabel, detIdLabel, detTypeLabel, detLocationLabel, detDateLabel, detReporterLabel, detDescLabel, detStatusBadge, detSeverityBadge;

    @FXML private CheckBox chkActive, chkMonitoring, chkResolved;
    @FXML private StackPane statusUpdateOverlay;
    @FXML private Label updateOverlaySubtitle;
    @FXML private ComboBox<String> updateStatusCombo;



    private WebEngine engine;
    private ObservableList<Incident> allIncidents;
    private boolean mapReady = false;
    private MainController mainController;
    private Incident currentSelectedIncident = null;
    private Incident pendingDetailView = null;

    private static final double CENTER_LAT = 12.8797, CENTER_LNG = 121.7740;
    private static final int ZOOM = 6;

    public void setMainController(MainController mc) { this.mainController = mc; }

    public void setCurrentUser(User currentUser) {
        // Let the User object decide its own permissions!
        boolean canUpdate = currentUser != null && currentUser.canUpdateIncidentStatus();
        btnUpdateStatus.setVisible(canUpdate);
        btnUpdateStatus.setManaged(canUpdate);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        initTable();
        initFilters();
        initMap();
    }

    private void initTable() {
        applyModernTableStyles();
        idColumn.setCellValueFactory(new PropertyValueFactory<>("incidentID"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        locationColumn.setCellValueFactory(new PropertyValueFactory<>("location"));
        severityColumn.setCellValueFactory(new PropertyValueFactory<>("severity"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        typeColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) { setText(null); setStyle(""); return; }
                setText(type); setStyle("-fx-font-weight: bold; -fx-text-fill: " + colorFor(type) + "; -fx-alignment: CENTER-LEFT;");
            }
        });

        severityColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String sev, boolean empty) {
                super.updateItem(sev, empty);
                if (empty || sev == null) { setGraphic(null); return; }
                Label badge = new Label(sev.toUpperCase()); badge.setStyle(severityBadgeStyle(sev));
                badge.setMaxWidth(Double.MAX_VALUE); badge.setAlignment(Pos.CENTER);
                setGraphic(badge); setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); return; }
                Label badge = new Label(status); badge.setStyle(statusBadgeStyle(status));
                badge.setMaxWidth(Double.MAX_VALUE); badge.setAlignment(Pos.CENTER);
                setGraphic(badge); setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        incidentTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) showIncidentDetailsPanel(selected);
        });
    }

    private void initFilters() {
        typeFilterCombo.setItems(FXCollections.observableArrayList("All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"));
        updateStatusCombo.setItems(FXCollections.observableArrayList("Active", "Monitoring", "Resolved"));
    }

    // NEW: Toggle List Visibility
    @FXML private void toggleList() {
        isListVisible = !isListVisible;
        listPanel.setVisible(isListVisible);
        listPanel.setManaged(isListVisible);
        btnShowList.setVisible(!isListVisible);
        btnShowList.setManaged(!isListVisible);

        // Force the map to recalculate its size after the layout shifts
        if (mapReady) {
            PauseTransition pause = new PauseTransition(Duration.millis(50));
            pause.setOnFinished(e -> engine.executeScript("map.invalidateSize();"));
            pause.play();
        }
    }

    private void initMap() {
        engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 DisasterReportApp/1.0");

        engine.setOnAlert(event -> {
            String data = event.getData();
            if (data != null && data.startsWith("GO_TO_DETAILS:")) {
                try {
                    int id = Integer.parseInt(data.substring(14));
                    Platform.runLater(() -> {
                        allIncidents.stream().filter(i -> i.getIncidentID() == id).findFirst().ifPresent(i -> {
                            incidentTable.getSelectionModel().select(i);
                            incidentTable.scrollTo(i);
                        });
                    });
                } catch (NumberFormatException e) {}
            }
        });

        engine.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapReady = true;
                PauseTransition pause = new PauseTransition(Duration.millis(150));
                pause.setOnFinished(e -> {
                    engine.executeScript("map.invalidateSize();");
                    if (incidentTable.getItems() != null) pushMarkers(incidentTable.getItems());
                    if (pendingDetailView != null) { flyToIncident(pendingDetailView); pendingDetailView = null; }
                });
                pause.play();
            }
        });
        try { loadMapPage(); } catch (IOException e) { e.printStackTrace(); }
    }

    private void applyModernTableStyles() {
        String css = """
            .table-view { -fx-background-color: transparent; -fx-border-width: 0; -fx-padding: 0; }
            .table-view .column-header-background { -fx-background-color: transparent; -fx-border-width: 0 0 1px 0; -fx-border-color: #e2e5ea; }
            .table-view .column-header { -fx-background-color: transparent; -fx-border-width: 0; }
            .table-view .column-header .label { -fx-text-fill: #9ca3af; -fx-font-weight: bold; -fx-font-size: 11px; }
            .table-row-cell { -fx-background-color: transparent; -fx-border-width: 0 0 1px 0; -fx-border-color: #f3f4f6; }
            .table-row-cell:empty { -fx-border-width: 0; }
            .table-row-cell:hover { -fx-background-color: #f9fafb; }
            .table-row-cell:selected { -fx-background-color: #fff7ed; }
            .table-cell { -fx-border-width: 0; -fx-padding: 12px 8px; -fx-font-size: 12px; -fx-text-fill: #374151; -fx-alignment: CENTER-LEFT; }
            """;
        String dataUri = "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes());
        incidentTable.getStylesheets().add(dataUri);
    }

    public void loadIncidents(List<Incident> incidents) {
        this.allIncidents = FXCollections.observableArrayList(incidents);
        chkActive.setSelected(true); chkMonitoring.setSelected(true); chkResolved.setSelected(false);
        applyFilters();
    }

    public void viewSpecificIncident(int id) {
        Platform.runLater(() -> {
            chkResolved.setSelected(true); applyFilters();
            allIncidents.stream().filter(i -> i.getIncidentID() == id).findFirst().ifPresent(i -> {
                incidentTable.getSelectionModel().select(i);
                incidentTable.scrollTo(i);
            });
        });
    }

    @FXML private void handleRefresh() { loadIncidents(DatabaseManager.getInstance().getIncidents()); }
    @FXML private void handleFilter() { applyFilters(); }
    @FXML private void handleClearFilters() {
        searchField.clear(); typeFilterCombo.setValue("All");
        chkActive.setSelected(true); chkMonitoring.setSelected(true); chkResolved.setSelected(false);
        applyFilters();
    }

    private void applyFilters() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String type = typeFilterCombo.getValue();
        boolean showActive = chkActive.isSelected(), showMonitoring = chkMonitoring.isSelected(), showResolved = chkResolved.isSelected();

        ObservableList<Incident> filtered = allIncidents.filtered(i -> {
            boolean keyMatch = keyword.isEmpty() || i.getLocation().toLowerCase().contains(keyword) || i.getType().toLowerCase().contains(keyword);
            boolean typeMatch = type == null || type.equals("All") || i.getType().equals(type);
            boolean statusMatch = ("Active".equals(i.getStatus()) && showActive) || ("Monitoring".equals(i.getStatus()) && showMonitoring) || ("Resolved".equals(i.getStatus()) && showResolved);
            return keyMatch && typeMatch && statusMatch;
        });

        incidentTable.setItems(filtered);
        renderMarkers(filtered);
    }

    public void showIncidentDetailsPanel(Incident inc) {
        this.currentSelectedIncident = inc;
        Platform.runLater(() -> {
            // Force the panel to open and hide the floating button
            isDetailsCollapsed = false;
            detailsPanel.setVisible(true);
            detailsPanel.setManaged(true);
            btnShowDetails.setVisible(false);
            btnShowDetails.setManaged(false);

            // Populate data...
            detIdLabel.setText("Incident #" + inc.getIncidentID());
            detTypeLabel.setText(inc.getType());
            detLocationLabel.setText(inc.getLocation());
            detReporterLabel.setText(inc.getReportedBy() != null ? inc.getReportedBy() : "Unknown");
            detDescLabel.setText(inc.getDescription() != null && !inc.getDescription().isEmpty() ? inc.getDescription() : "—");
            detDateLabel.setText(inc.getDate() != null ? inc.getDate().toString() : "—");
            detStatusBadge.setText(inc.getStatus()); detStatusBadge.setStyle(statusBadgeStyle(inc.getStatus()));
            detSeverityBadge.setText(inc.getSeverity() != null ? inc.getSeverity().toUpperCase() : "MEDIUM"); detSeverityBadge.setStyle(severityBadgeStyle(inc.getSeverity()));

            if (inc.getImageData() != null && !inc.getImageData().trim().isEmpty()) {
                btnViewPhoto.setVisible(true); btnViewPhoto.setManaged(true);
            } else {
                btnViewPhoto.setVisible(false); btnViewPhoto.setManaged(false);
            }

            if (mapReady) { flyToIncident(inc); } else { pendingDetailView = inc; }
        });
    }

    @FXML private void closeDetailsPanel() {
        // Completely hide everything and deselect the incident
        detailsPanel.setVisible(false);
        detailsPanel.setManaged(false);
        btnShowDetails.setVisible(false);
        btnShowDetails.setManaged(false);
        isDetailsCollapsed = false;
        currentSelectedIncident = null;

        incidentTable.getSelectionModel().clearSelection();

        // Zoom back out to the country view
        try { engine.executeScript("map.flyTo([" + CENTER_LAT + ", " + CENTER_LNG + "], " + ZOOM + ", { animate: true });"); } catch (Exception e) {}
    }

    @FXML private void handleViewPhoto() {
        if (currentSelectedIncident != null && currentSelectedIncident.getImageData() != null) {
            try {
                byte[] imgBytes = java.util.Base64.getDecoder().decode(currentSelectedIncident.getImageData());
                java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(imgBytes);
                javafx.scene.image.Image img = new javafx.scene.image.Image(bais);
                com.example.disasterreport.util.ModernDialog.showImagePopup("Attached Photo Evidence", img);
            } catch (Exception e) {
                com.example.disasterreport.util.ModernDialog.showMessage("Error", "Could not load image. It may be corrupted.", true);
            }
        }
    }

    private void flyToIncident(Incident inc) {
        double lat = inc.getLatitude() != 0 ? inc.getLatitude() : CENTER_LAT;
        double lng = inc.getLongitude() != 0 ? inc.getLongitude() : CENTER_LNG;
        try {
            engine.executeScript("map.invalidateSize({animate: false});");
            engine.executeScript("map.flyTo([" + lat + ", " + lng + "], 18, { animate: true, duration: 1.5 });");
        } catch (Exception e) {}
    }

    @FXML private void handleUpdateStatus() {
        if (currentSelectedIncident == null) return;
        updateOverlaySubtitle.setText("Change status for Incident #" + currentSelectedIncident.getIncidentID() + " (" + currentSelectedIncident.getType() + ")");
        updateStatusCombo.setValue(currentSelectedIncident.getStatus());
        statusUpdateOverlay.setVisible(true); statusUpdateOverlay.setManaged(true);
    }

    @FXML private void closeUpdateOverlay() { statusUpdateOverlay.setVisible(false); statusUpdateOverlay.setManaged(false); }

    @FXML private void saveUpdateStatus() {
        if (currentSelectedIncident == null || updateStatusCombo.getValue() == null) return;
        String newStatus = updateStatusCombo.getValue();
        currentSelectedIncident.setStatus(newStatus);
        DatabaseManager.getInstance().updateIncident(currentSelectedIncident);
        incidentTable.refresh();
        if ("Resolved".equals(newStatus) && !chkResolved.isSelected()) { closeDetailsPanel(); } else { showIncidentDetailsPanel(currentSelectedIncident); }
        applyFilters();
        if (mainController != null) mainController.refreshSidebarStats();
        closeUpdateOverlay();
    }

    private void renderMarkers(List<Incident> incidents) {
        markerCountLabel.setText(incidents.size() + " incidents");
        if (mapReady) pushMarkers(incidents);
    }

    private void pushMarkers(List<Incident> incidents) {
        StringBuilder js = new StringBuilder("var data = [");
        boolean first = true;
        for (Incident inc : incidents) {
            if (inc.getLatitude() == 0) continue;
            if (!first) js.append(",");
            js.append("{id:").append(inc.getIncidentID()).append(",lat:").append(inc.getLatitude()).append(",lng:").append(inc.getLongitude()).append(",color:'").append(colorFor(inc.getType())).append("',type:'").append(escapeJs(inc.getType())).append("',loc:'").append(escapeJs(inc.getLocation())).append("'}");
            first = false;
        }
        js.append("]; renderAll(data);");
        try { engine.executeScript(js.toString()); } catch (Exception e) {}
    }

    private void loadMapPage() throws IOException {
        Path tempDir = Files.createTempDirectory("disastermap_"); tempDir.toFile().deleteOnExit();
        copyResource("/com/example/disasterreport/leaflet/leaflet.css", tempDir.resolve("leaflet.css"));
        copyResource("/com/example/disasterreport/leaflet/leaflet.js", tempDir.resolve("leaflet.js"));
        Files.writeString(tempDir.resolve("map.html"), buildMapHtml());
        engine.load(tempDir.resolve("map.html").toUri().toString());
    }

    private void copyResource(String cp, Path dest) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(cp)) { if (is != null) Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING); }
    }

    private String buildMapHtml() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset='utf-8'/>
                <link rel='stylesheet' href='leaflet.css'/>
                <style>
                    html, body, #map { 
                        height: 100%; 
                        width: 100%; 
                        margin: 0; 
                        padding: 0; 
                        background: #e5e7eb; 
                        font-family: 'Segoe UI', Arial, sans-serif; 
                    } 
                    .custom-tooltip { 
                        font-weight: bold; 
                        padding: 4px 8px; 
                        border-radius: 4px; 
                        box-shadow: 0 2px 5px rgba(0,0,0,0.2); 
                        border: none; 
                    } 
                    .leaflet-interactive { 
                        cursor: pointer !important; 
                    }
                </style>
            </head>
            <body>
                <div id='map'></div>
                
                <script src='leaflet.js'></script>
                <script>
                    L.Browser.any3d = false; 
                    
                    var map = L.map('map', { 
                        center: [12.8797, 121.7740], 
                        zoom: 6, 
                        zoomControl: true, 
                        scrollWheelZoom: true, 
                        doubleClickZoom: true 
                    }); 
                    
                    var onlineUrl = 'https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png'; 
                    var offlineUrl = 'http://127.0.0.1:8080/tiles/{z}/{x}/{y}.png'; 
                    var tileLayer = L.tileLayer(onlineUrl, { maxZoom: 18, minZoom: 2, keepBuffer: 4 }); 
                    
                    // Offline Fallback Logic
                    tileLayer.on('tileerror', function(e) { 
                        var fallback = offlineUrl.replace('{z}', e.coords.z).replace('{x}', e.coords.x).replace('{y}', e.coords.y); 
                        if (e.tile.src !== fallback) { 
                            e.tile.src = fallback; 
                        } 
                    }); 
                    
                    tileLayer.addTo(map); 
                    
                    // Handle resizing issues when JavaFX panels open/close
                    window.addEventListener('load', function() { 
                        setTimeout(function(){ map.invalidateSize(); }, 250); 
                    }); 
                    
                    window.addEventListener('resize', function(){ 
                        map.invalidateSize(); 
                    }); 
                    
                    var markerLayer = L.layerGroup().addTo(map); 
                    
                    // Draw the pins from Java
                    function renderAll(data) { 
                        markerLayer.clearLayers(); 
                        data.forEach(function(d) { 
                            var iconHtml = '<div style="width:16px;height:16px;border-radius:50%;background:'+d.color+';border:2px solid #fff;box-shadow:0 2px 5px rgba(0,0,0,0.4);"></div>'; 
                            var icon = L.divIcon({className: '', html: iconHtml, iconSize: [16,16], iconAnchor: [8,8]}); 
                            var m = L.marker([d.lat, d.lng], {icon: icon}).addTo(markerLayer); 
                            
                            m.bindTooltip(d.type + ' &mdash; ' + d.loc, {direction: 'top', offset: [0, -10], className: 'custom-tooltip'}); 
                            
                            // Send the click back to Java
                            m.on('click', function() { 
                                alert('GO_TO_DETAILS:' + d.id); 
                            }); 
                        }); 
                    } 
                </script>
            </body>
            </html>
            """;
    }

    private String colorFor(String t) { return switch(t){case "Flood"->"#3b82f6";case "Fire"->"#ef4444";case "Earthquake"->"#f97316";case "Typhoon"->"#8b5cf6";case "Landslide"->"#a16207";default->"#6b7280";}; }
    private String escapeJs(String s) { return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\n", " ").replace("\r", ""); }

    private String severityBadgeStyle(String s) {
        String bg, fg;
        switch (s != null ? s.toUpperCase() : "MEDIUM") {
            case "LOW" -> { bg = "#dbeafe"; fg = "#1d4ed8"; }
            case "MEDIUM" -> { bg = "#fef3c7"; fg = "#b45309"; }
            case "HIGH" -> { bg = "#ffedd5"; fg = "#c2410c"; }
            case "CRITICAL" -> { bg = "#fee2e2"; fg = "#b91c1c"; }
            default -> { bg = "#f3f4f6"; fg = "#4b5563"; }
        }
        return String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 10px; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 3 8;", bg, fg);
    }

    private String statusBadgeStyle(String s) {
        String bg, fg;
        switch (s != null ? s : "Monitoring") {
            case "Active" -> { bg = "#ffe4e1"; fg = "#c0392b"; }
            case "Monitoring" -> { bg = "#fef9c3"; fg = "#92400e"; }
            case "Resolved" -> { bg = "#dcfce7"; fg = "#15803d"; }
            default -> { bg = "#f3f4f6"; fg = "#6b7280"; }
        }
        return String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 10px; -fx-font-weight: bold; -background-radius: 20; -fx-padding: 3 8;", bg, fg);
    }

    @FXML private void toggleDetails() {
        if (currentSelectedIncident == null) return; // Do nothing if no incident is active

        isDetailsCollapsed = !isDetailsCollapsed;

        // Hide/Show the right panel
        detailsPanel.setVisible(!isDetailsCollapsed);
        detailsPanel.setManaged(!isDetailsCollapsed);

        // Hide/Show the floating button
        btnShowDetails.setVisible(isDetailsCollapsed);
        btnShowDetails.setManaged(isDetailsCollapsed);

        // Force map to recalculate its size so it expands to fill the empty space
        if (mapReady) {
            PauseTransition pause = new PauseTransition(Duration.millis(50));
            pause.setOnFinished(e -> engine.executeScript("map.invalidateSize();"));
            pause.play();
        }
    }
}