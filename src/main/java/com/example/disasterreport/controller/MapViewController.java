package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
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
import netscape.javascript.JSObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.List;
import java.util.ResourceBundle;

public class MapViewController implements Initializable {

    // Main UI
    @FXML private TableView<Incident> incidentTable;
    @FXML private TableColumn<Incident, Integer> idColumn;
    @FXML private TableColumn<Incident, String> typeColumn, locationColumn, severityColumn, statusColumn;
    @FXML private WebView mapWebView;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private Label markerCountLabel, detIdLabel, detTypeLabel, detLocationLabel, detDateLabel, detReporterLabel, detDescLabel, detStatusBadge, detSeverityBadge;
    @FXML private VBox detailsPanel;
    @FXML private Button btnUpdateStatus;

    // Status Checklist Elements
    @FXML private CheckMenuItem chkActive;
    @FXML private CheckMenuItem chkMonitoring;
    @FXML private CheckMenuItem chkResolved;

    // Custom Popup Overlay Elements
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

    // ── THE SILVER BULLET JS BRIDGE ─────────────────────────────────────────
    private final JSBridge jsBridge = new JSBridge();

    public class JSBridge {
        public void goToDetails(String idStr) {
            try {
                int id = Integer.parseInt(idStr);
                allIncidents.stream().filter(i -> i.getIncidentID() == id).findFirst().ifPresent(i ->
                        Platform.runLater(() -> {
                            incidentTable.getSelectionModel().select(i);
                            incidentTable.scrollTo(i);
                        })
                );
            } catch (Exception e) {
                System.err.println("Failed to parse Incident ID from Map click.");
            }
        }
    }
    // ────────────────────────────────────────────────────────────────────────

    public void setMainController(MainController mc) { this.mainController = mc; }
    public void setCurrentUserRole(String role) {
        boolean canUpdate = "admin".equals(role) || "responder".equals(role);
        btnUpdateStatus.setVisible(canUpdate); btnUpdateStatus.setManaged(canUpdate);
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
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
                Label badge = new Label(sev.toUpperCase());
                badge.setStyle(severityBadgeStyle(sev));
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setAlignment(Pos.CENTER);
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        statusColumn.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setGraphic(null); return; }
                Label badge = new Label(status);
                badge.setStyle(statusBadgeStyle(status));
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setAlignment(Pos.CENTER);
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        incidentTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) showIncidentDetailsPanel(selected);
        });

        typeFilterCombo.setItems(FXCollections.observableArrayList("All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"));
        updateStatusCombo.setItems(FXCollections.observableArrayList("Active", "Monitoring", "Resolved"));

        engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

        engine.getLoadWorker().stateProperty().addListener((obs, old, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapReady = true;
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaApp", jsBridge);

                PauseTransition pause = new PauseTransition(Duration.millis(150));
                pause.setOnFinished(e -> {
                    engine.executeScript("map.invalidateSize();");
                    pushMarkers(incidentTable.getItems());

                    if (pendingDetailView != null) {
                        flyToIncident(pendingDetailView);
                        pendingDetailView = null;
                    }
                });
                pause.play();
            }
        });
        try { loadMapPage(); } catch (IOException e) { e.printStackTrace(); }
    }

    private void applyModernTableStyles() {
        String css = ".table-view { -fx-background-color: white; -fx-border-width: 0; -fx-padding: 0; }\n" +
                ".table-view .column-header-background { -fx-background-color: white; -fx-border-width: 0 0 1px 0; -fx-border-color: #e2e5ea; }\n" +
                ".table-view .column-header { -fx-background-color: transparent; -fx-border-width: 0; }\n" +
                ".table-view .column-header .label { -fx-text-fill: #9ca3af; -fx-font-weight: bold; -fx-font-size: 11px; }\n" +
                ".table-row-cell { -fx-background-color: white; -fx-border-width: 0 0 1px 0; -fx-border-color: #f3f4f6; }\n" +
                ".table-row-cell:empty { -fx-border-width: 0; }\n" +
                ".table-row-cell:hover { -fx-background-color: #f9fafb; }\n" +
                ".table-row-cell:selected { -fx-background-color: #fff7ed; }\n" +
                ".table-cell { -fx-border-width: 0; -fx-padding: 12px 8px; -fx-font-size: 13px; -fx-text-fill: #374151; -fx-alignment: CENTER-LEFT; }";
        String dataUri = "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes());
        incidentTable.getStylesheets().add(dataUri);
    }

    // ── DATA LOADING & FILTERING ──────────────────────────────────────────

    public void loadIncidents(List<Incident> incidents) {
        this.allIncidents = FXCollections.observableArrayList(incidents);

        // Reset checklist to show only active/monitoring by default
        chkActive.setSelected(true);
        chkMonitoring.setSelected(true);
        chkResolved.setSelected(false);

        applyFilters();
    }

    public void viewSpecificIncident(int id) {
        Platform.runLater(() -> {
            // Force turn on "Resolved" checkbox just in case the target is a resolved incident
            chkResolved.setSelected(true);
            applyFilters();

            allIncidents.stream().filter(i -> i.getIncidentID() == id).findFirst().ifPresent(i -> {
                incidentTable.getSelectionModel().select(i);
                incidentTable.scrollTo(i);
            });
        });
    }

    @FXML private void handleRefresh() {
        loadIncidents(DatabaseManager.getInstance().getIncidents());
    }

    @FXML private void handleFilter() { applyFilters(); }

    @FXML private void handleClearFilters() {
        searchField.clear();
        typeFilterCombo.setValue("All");

        chkActive.setSelected(true);
        chkMonitoring.setSelected(true);
        chkResolved.setSelected(false);

        applyFilters();
    }

    private void applyFilters() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().toLowerCase();
        String type = typeFilterCombo.getValue();

        boolean showActive = chkActive.isSelected();
        boolean showMonitoring = chkMonitoring.isSelected();
        boolean showResolved = chkResolved.isSelected();

        ObservableList<Incident> filtered = allIncidents.filtered(i -> {
            boolean keyMatch = keyword.isEmpty() || i.getLocation().toLowerCase().contains(keyword) || i.getType().toLowerCase().contains(keyword);
            boolean typeMatch = type == null || type.equals("All") || i.getType().equals(type);

            boolean statusMatch = false;
            String st = i.getStatus();
            if ("Active".equals(st) && showActive) statusMatch = true;
            if ("Monitoring".equals(st) && showMonitoring) statusMatch = true;
            if ("Resolved".equals(st) && showResolved) statusMatch = true;

            return keyMatch && typeMatch && statusMatch;
        });

        incidentTable.setItems(filtered);
        renderMarkers(filtered);
    }

    // ── DETAILS PANEL ─────────────────────────────────────────────────────

    public void showIncidentDetailsPanel(Incident inc) {
        this.currentSelectedIncident = inc;
        Platform.runLater(() -> {
            detailsPanel.setVisible(true);
            detIdLabel.setText("Incident #" + inc.getIncidentID());
            detTypeLabel.setText(inc.getType());
            detLocationLabel.setText(inc.getLocation());
            detReporterLabel.setText(inc.getReportedBy());
            detDescLabel.setText(inc.getDescription() != null && !inc.getDescription().isEmpty() ? inc.getDescription() : "—");
            if (inc.getDate() != null) {
                detDateLabel.setText(inc.getDate().toString());
            } else {
                detDateLabel.setText("—");
            }

            detStatusBadge.setText(inc.getStatus());
            detStatusBadge.setStyle(statusBadgeStyle(inc.getStatus()));
            detSeverityBadge.setText(inc.getSeverity() != null ? inc.getSeverity().toUpperCase() : "MEDIUM");
            detSeverityBadge.setStyle(severityBadgeStyle(inc.getSeverity()));

            if (mapReady) {
                flyToIncident(inc);
            } else {
                pendingDetailView = inc;
            }
        });
    }

    private void flyToIncident(Incident inc) {
        double lat = inc.getLatitude() != 0 ? inc.getLatitude() : CENTER_LAT;
        double lng = inc.getLongitude() != 0 ? inc.getLongitude() : CENTER_LNG;
        try {
            engine.executeScript("map.invalidateSize({animate: false});");
            engine.executeScript("map.flyTo([" + lat + ", " + lng + "], 18, { animate: true, duration: 1.5 });");
        } catch (Exception e) {}
    }

    @FXML private void closeDetailsPanel() { detailsPanel.setVisible(false); incidentTable.getSelectionModel().clearSelection(); try { engine.executeScript("map.flyTo([" + CENTER_LAT + ", " + CENTER_LNG + "], " + ZOOM + ", { animate: true });"); } catch (Exception e) {} }

    // ── CUSTOM OVERLAY STATUS UPDATE LOGIC ─────────────────────────────

    @FXML private void handleUpdateStatus() {
        if (currentSelectedIncident == null) return;

        updateOverlaySubtitle.setText("Change status for Incident #" + currentSelectedIncident.getIncidentID() + " (" + currentSelectedIncident.getType() + ")");
        updateStatusCombo.setValue(currentSelectedIncident.getStatus());

        statusUpdateOverlay.setVisible(true);
        statusUpdateOverlay.setManaged(true);
    }

    @FXML private void closeUpdateOverlay() {
        statusUpdateOverlay.setVisible(false);
        statusUpdateOverlay.setManaged(false);
    }

    @FXML private void saveUpdateStatus() {
        if (currentSelectedIncident == null || updateStatusCombo.getValue() == null) return;

        String newStatus = updateStatusCombo.getValue();
        currentSelectedIncident.setStatus(newStatus);
        DatabaseManager.getInstance().updateIncident(currentSelectedIncident);

        incidentTable.refresh();

        // Hide card if it was marked Resolved and the "Resolved" checkbox isn't currently checked
        if ("Resolved".equals(newStatus) && !chkResolved.isSelected()) {
            closeDetailsPanel();
        } else {
            showIncidentDetailsPanel(currentSelectedIncident);
        }

        applyFilters();
        if (mainController != null) mainController.refreshSidebarStats();

        closeUpdateOverlay();
    }

    // ── MAP RENDERING ─────────────────────────────────────────────────────

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
        Path tempDir = Files.createTempDirectory("disastermap_");
        tempDir.toFile().deleteOnExit();
        copyResource("/com/example/disasterreport/leaflet/leaflet.css", tempDir.resolve("leaflet.css"));
        copyResource("/com/example/disasterreport/leaflet/leaflet.js", tempDir.resolve("leaflet.js"));
        Files.writeString(tempDir.resolve("map.html"), buildMapHtml());
        engine.load(tempDir.resolve("map.html").toUri().toString());
    }

    private void copyResource(String cp, Path dest) throws IOException { try (InputStream is = getClass().getResourceAsStream(cp)) { if (is != null) Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING); } }

    private String buildMapHtml() {
        StringBuilder h = new StringBuilder();
        h.append("<!DOCTYPE html><html><head><meta charset='utf-8'/><link rel='stylesheet' href='leaflet.css'/>");
        h.append("<style>html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: #e5e7eb; font-family: 'Segoe UI', Arial, sans-serif; } ");
        h.append(".custom-tooltip { font-weight: bold; padding: 4px 8px; border-radius: 4px; box-shadow: 0 2px 5px rgba(0,0,0,0.2); border: none; } ");
        h.append(".leaflet-interactive { cursor: pointer !important; }</style></head>");
        h.append("<body><div id='map'></div><script src='leaflet.js'></script><script>");
        h.append("L.Browser.any3d = false; ");
        h.append("var map = L.map('map', { center: [").append(CENTER_LAT).append(", ").append(CENTER_LNG).append("], zoom: ").append(ZOOM).append(", zoomControl: true, scrollWheelZoom: true, doubleClickZoom: true }); ");
        h.append("L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 19, minZoom: 2, subdomains: 'abcd', updateWhenIdle: false, updateWhenZooming: false, keepBuffer: 4 }).addTo(map); ");
        h.append("window.addEventListener('load', function() { setTimeout(function(){ map.invalidateSize(); }, 250); }); window.addEventListener('resize', function(){ map.invalidateSize(); }); ");
        h.append("var markerLayer = L.layerGroup().addTo(map); ");
        h.append("function renderAll(data) { markerLayer.clearLayers(); data.forEach(function(d) { ");
        h.append("var iconHtml = '<div style=\"width:16px;height:16px;border-radius:50%;background:'+d.color+';border:2px solid #fff;box-shadow:0 2px 5px rgba(0,0,0,0.4);\"></div>'; ");
        h.append("var icon = L.divIcon({className: '', html: iconHtml, iconSize: [16,16], iconAnchor: [8,8]}); ");
        h.append("var m = L.marker([d.lat, d.lng], {icon: icon}).addTo(markerLayer); ");
        h.append("m.bindTooltip(d.type + ' &mdash; ' + d.loc, {direction: 'top', offset: [0, -10], className: 'custom-tooltip'}); ");
        h.append("m.on('click', function() { if (window.javaApp) { javaApp.goToDetails(d.id.toString()); } }); ");
        h.append("}); } </script></body></html>");
        return h.toString();
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
        return String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 3 10;", bg, fg);
    }

    private String statusBadgeStyle(String s) {
        String bg, fg;
        switch (s != null ? s : "Monitoring") {
            case "Active" -> { bg = "#ffe4e1"; fg = "#c0392b"; }
            case "Monitoring" -> { bg = "#fef9c3"; fg = "#92400e"; }
            case "Resolved" -> { bg = "#dcfce7"; fg = "#15803d"; }
            default -> { bg = "#f3f4f6"; fg = "#6b7280"; }
        }
        return String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 3 10;", bg, fg);
    }
}