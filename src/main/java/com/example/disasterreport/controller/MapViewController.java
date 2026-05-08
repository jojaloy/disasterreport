package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class MapViewController implements Initializable {

    @FXML private WebView          mapWebView;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private Label            markerCountLabel;
    @FXML private Label            statusLabel;

    private WebEngine      engine;
    private List<Incident> allIncidents  = new ArrayList<>();
    private boolean        mapReady      = false;
    private List<Incident> pendingRender = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        // Proper browser UA avoids tile-server refusals
        engine.setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36"
        );

        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        typeFilterCombo.getSelectionModel().selectFirst();

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapReady = true;
                statusLabel.setText("Map ready.");
                if (pendingRender != null) {
                    // Small delay lets Leaflet finish its own init before we push markers
                    javafx.application.Platform.runLater(() -> {
                        pushMarkers(pendingRender);
                        pendingRender = null;
                    });
                }
            } else if (newState == Worker.State.FAILED) {
                statusLabel.setText("Map failed to load – check internet connection.");
            }
        });

        // Load the HTML exactly once — never call loadContent() again
        engine.loadContent(buildBaseHtml(), "text/html");
    }

    public void loadIncidents(List<Incident> incidents) {
        this.allIncidents = incidents;
        renderMarkers(incidents);
    }

    @FXML private void handleShowAll() {
        typeFilterCombo.getSelectionModel().select("All");
        renderMarkers(allIncidents);
    }

    @FXML private void handleRefresh() {
        renderMarkers(getFilteredList());
    }

    @FXML private void handleFilter() {
        renderMarkers(getFilteredList());
    }

    private List<Incident> getFilteredList() {
        String sel = typeFilterCombo.getValue();
        if (sel == null || sel.equals("All")) return allIncidents;
        return allIncidents.stream().filter(i -> sel.equals(i.getType())).toList();
    }

    private void renderMarkers(List<Incident> incidents) {
        markerCountLabel.setText(incidents.size() + " markers");
        statusLabel.setText(incidents.isEmpty() ? "No incidents to display." : "Displaying " + incidents.size() + " incidents.");

        if (!mapReady) {
            pendingRender = incidents;
        } else {
            pushMarkers(incidents);
        }
    }

    private void pushMarkers(List<Incident> incidents) {
        StringBuilder js = new StringBuilder();
        js.append("clearMarkers();");

        for (Incident inc : incidents) {
            double lat = inc.getLatitude();
            double lng = inc.getLongitude();
            if (lat == 0.0 && lng == 0.0) continue;

            String color = colorFor(inc.getType());
            String icon  = iconFor(inc.getType());
            String popup = escapeJs(
                    "<b>" + icon + " " + inc.getType() + "</b><br>" +
                            "📍 " + inc.getLocation() +
                            (inc.getDescription() != null && !inc.getDescription().isEmpty()
                                    ? "<br><span style='color:#6b7280'>" + inc.getDescription() + "</span>" : "") +
                            "<br><b>Status:</b> " + inc.getStatus() +
                            "<br><b>Date:</b> " + inc.getDateString() +
                            "<br><b>Reported by:</b> " + inc.getReportedBy()
            );

            js.append("addMarker(")
                    .append(lat).append(",")
                    .append(lng).append(",'")
                    .append(color).append("','")
                    .append(icon).append("','")
                    .append(popup).append("');");
        }

        engine.executeScript(js.toString());
    }

    private String buildBaseHtml() {
        StringBuilder html = new StringBuilder();

        // Philippines center
        double centerLat = 12.8797;
        double centerLng = 121.7740;
        int    zoom      = 6;

        html.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<head>")
                .append("<meta charset='utf-8'/>")
                .append("<meta name='viewport' content='width=device-width, initial-scale=1'/>")
                .append("<title>Disaster Map</title>")
                .append("<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css' crossorigin=''/>")
                .append("<style>")
                // Critical: html/body/map must fill 100% with NO overflow
                .append("* { margin:0; padding:0; box-sizing:border-box; }")
                .append("html { width:100%; height:100%; overflow:hidden; }")
                .append("body { width:100%; height:100%; overflow:hidden; background:#e8edf5; }")
                .append("#map { position:absolute; top:0; left:0; right:0; bottom:0; }")
                // Custom popup styling
                .append(".leaflet-popup-content-wrapper { border-radius:8px; font-family:sans-serif; font-size:13px; }")
                .append(".leaflet-popup-content { margin: 10px 14px; line-height:1.6; }")
                // Pulse animation for active markers
                .append("@keyframes pulse { 0%{transform:scale(1);opacity:1} 50%{transform:scale(1.3);opacity:0.7} 100%{transform:scale(1);opacity:1} }")
                .append(".pulse-marker { animation: pulse 2s infinite; }")
                .append("</style>")
                .append("</head>")
                .append("<body>")
                .append("<div id='map'></div>")
                .append("<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js' crossorigin=''></script>")
                .append("<script>")

                // ── Map init ──
                .append("var map = L.map('map', {")
                .append("  center:[").append(centerLat).append(",").append(centerLng).append("],")
                .append("  zoom:").append(zoom).append(",")
                .append("  zoomControl:true,")
                .append("  scrollWheelZoom:true,")
                // These two options prevent the gray tile flicker on resize
                .append("  preferCanvas:true,")
                .append("  renderer: L.canvas()")
                .append("});")

                // Tile layer with keepBuffer to reduce choppy reloading
                .append("L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {")
                .append("  attribution:'&copy; OpenStreetMap contributors',")
                .append("  maxZoom:19,")
                .append("  keepBuffer:4,")       // <-- keeps more tiles buffered = less choppiness
                .append("  updateWhenZooming:false,")  // <-- don't repaint mid-zoom
                .append("  updateWhenIdle:true")
                .append("}).addTo(map);")

                // Marker layer group
                .append("var markerLayer = L.layerGroup().addTo(map);")

                // Fix tile fragmentation: invalidate after layout settles
                // Two calls: one quick, one after a longer delay for slow systems
                .append("setTimeout(function(){ map.invalidateSize(false); }, 200);")
                .append("setTimeout(function(){ map.invalidateSize(false); }, 800);")
                .append("window.addEventListener('resize', function(){ map.invalidateSize(false); });")

                // ── Public API ──
                .append("function clearMarkers() { markerLayer.clearLayers(); }")

                .append("function addMarker(lat, lng, color, emoji, popupHtml) {")
                .append("  var icon = L.divIcon({")
                .append("    className:'',")
                .append("    html: '<div style=\"'")
                .append("         + 'width:30px;height:30px;'")
                .append("         + 'border-radius:50%;'")
                .append("         + 'background:'+color+';'")
                .append("         + 'border:3px solid white;'")
                .append("         + 'box-shadow:0 2px 8px rgba(0,0,0,0.35);'")
                .append("         + 'display:flex;align-items:center;justify-content:center;'")
                .append("         + 'font-size:14px;cursor:pointer;'")
                .append("         + '\">'+ emoji +'</div>',")
                .append("    iconSize:[30,30],")
                .append("    iconAnchor:[15,15],")
                .append("    popupAnchor:[0,-18]")
                .append("  });")
                .append("  L.marker([lat,lng],{icon:icon})")
                .append("   .bindPopup(popupHtml, {maxWidth:260})")
                .append("   .addTo(markerLayer);")
                .append("}")

                .append("</script>")
                .append("</body>")
                .append("</html>");

        return html.toString();
    }

    private String colorFor(String type) {
        if (type == null) return "#6b7280";
        return switch (type) {
            case "Flood"      -> "#3b82f6";
            case "Fire"       -> "#ef4444";
            case "Earthquake" -> "#f97316";
            case "Typhoon"    -> "#8b5cf6";
            case "Landslide"  -> "#a16207";
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

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'",  "\\'")
                .replace("\n", " ")
                .replace("\r", "");
    }
}