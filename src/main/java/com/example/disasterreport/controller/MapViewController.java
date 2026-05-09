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

    // ── Philippines center coordinates ────────────────────────────────────
    private static final double CENTER_LAT = 12.8797;
    private static final double CENTER_LNG = 121.7740;
    private static final int    ZOOM       = 6;

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);

        // Spoof a real browser user-agent so OSM and Leaflet CDN serve assets normally
        engine.setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36"
        );

        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        typeFilterCombo.getSelectionModel().selectFirst();

        // ── KEY FIX from SYSTEC approach ──────────────────────────────────
        // Wait for the full page (including Leaflet JS from CDN) to finish
        // loading before pushing any markers. This prevents the blank-tile /
        // choppy-grid bug that happens when executeScript() fires too early.
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapReady = true;
                statusLabel.setText("Map ready.");

                // Flush any markers that arrived before the page was ready
                if (pendingRender != null) {
                    pushMarkers(pendingRender);
                    pendingRender = null;
                }
            }
            if (newState == Worker.State.FAILED) {
                statusLabel.setText("Map failed to load. Check your internet connection.");
            }
        });

        // Load the HTML page exactly ONCE — never call loadContent() again.
        // This mirrors what SYSTEC does with its static iframe: the base page
        // never reloads, only the marker layer is updated.
        engine.loadContent(buildMapHtml(), "text/html");
    }

    /** Called from MainController once the view is shown. */
    public void loadIncidents(List<Incident> incidents) {
        this.allIncidents = new ArrayList<>(incidents);
        renderMarkers(incidents);
    }

    // ── Button handlers ───────────────────────────────────────────────────
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

    // ── Private helpers ───────────────────────────────────────────────────

    private List<Incident> getFilteredList() {
        String sel = typeFilterCombo.getValue();
        if (sel == null || sel.equals("All")) return allIncidents;
        return allIncidents.stream()
                .filter(i -> sel.equals(i.getType()))
                .toList();
    }

    private void renderMarkers(List<Incident> incidents) {
        markerCountLabel.setText(incidents.size() + " markers");
        statusLabel.setText(incidents.isEmpty()
                ? "No incidents to display."
                : incidents.size() + " incident(s) loaded.");

        if (!mapReady) {
            pendingRender = new ArrayList<>(incidents);
        } else {
            pushMarkers(incidents);
        }
    }

    /**
     * Pushes markers into the already-running Leaflet map via executeScript().
     *
     * SYSTEC lesson applied: the page is loaded once (like their static iframe).
     * Only the marker layer is cleared and redrawn — the OSM tiles stay intact
     * in WebKit's cache, so there is no choppiness or tile-grid artifact.
     */
    private void pushMarkers(List<Incident> incidents) {
        StringBuilder js = new StringBuilder();
        js.append("clearMarkers();");

        for (Incident inc : incidents) {
            double lat = inc.getLatitude();
            double lng = inc.getLongitude();
            if (lat == 0.0 && lng == 0.0) continue;   // skip ungeocoded rows

            String color  = colorFor(inc.getType());
            String popup  = escapeJs(buildPopup(inc));

            js.append("addMarker(")
                    .append(lat).append(",")
                    .append(lng).append(",'")
                    .append(color).append("','")
                    .append(popup).append("');");
        }

        engine.executeScript(js.toString());
    }

    private String buildPopup(Incident inc) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(inc.getType()).append("</b>");
        sb.append(" — ").append(inc.getLocation());
        if (inc.getDescription() != null && !inc.getDescription().isBlank()) {
            sb.append("<br>").append(inc.getDescription());
        }
        sb.append("<br><b>Status:</b> ").append(inc.getStatus());
        sb.append("<br><b>Date:</b> ").append(inc.getDateString());
        return sb.toString();
    }

    /**
     * Builds the single HTML page that is loaded exactly once.
     *
     * ── SYSTEC technique applied ─────────────────────────────────────────
     * SYSTEC's map works because their <iframe> fills 100 % of its container
     * with no overflow, and the containing element itself fills the viewport.
     * We replicate that here:
     *
     *   html, body { width:100%; height:100%; overflow:hidden; }
     *   #map       { width:100vw; height:100vh; }
     *
     * This is what prevents Leaflet from measuring a 0×0 container on first
     * paint and fragmenting the tile grid.  The invalidateSize() call + 300 ms
     * delay is kept as a belt-and-suspenders fix for JavaFX's deferred layout.
     *
     * Plain StringBuilder is used throughout to avoid String.format /
     * formatted() choking on % characters inside OSM tile URLs.
     */
    private String buildMapHtml() {
        StringBuilder h = new StringBuilder();

        h.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<head>")
                .append("<meta charset='utf-8'/>")
                .append("<meta name='viewport' content='width=device-width,initial-scale=1'/>")
                .append("<title>Disaster Map</title>")

                // ── Leaflet CSS ──────────────────────────────────────────────────
                .append("<link rel='stylesheet'")
                .append(" href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'")
                .append(" crossorigin=''/>")

                // ── SYSTEC-style full-bleed layout ───────────────────────────────
                // Every ancestor of #map must be 100 % tall with no overflow so
                // Leaflet can measure the correct pixel dimensions on first render.
                .append("<style>")
                .append("* { margin:0; padding:0; box-sizing:border-box; }")
                .append("html, body { width:100%; height:100%; overflow:hidden; }")
                .append("#map { width:100vw; height:100vh; background:#e8f0e8; }")
                .append("</style>")
                .append("</head>")
                .append("<body>")
                .append("<div id='map'></div>")

                // ── Leaflet JS ───────────────────────────────────────────────────
                .append("<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'")
                .append(" crossorigin=''></script>")

                .append("<script>")

                // Create the map centred on the Philippines
                .append("var map = L.map('map', {")
                .append("  center: [").append(CENTER_LAT).append(", ").append(CENTER_LNG).append("],")
                .append("  zoom: ").append(ZOOM).append(",")
                .append("  zoomControl: true,")
                .append("  scrollWheelZoom: true")
                .append("});")

                // OSM tile layer — same source SYSTEC's iframe uses
                .append("L.tileLayer(")
                .append("  'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',")
                .append("  { attribution: '&copy; OpenStreetMap contributors', maxZoom: 19 }")
                .append(").addTo(map);")

                // Marker layer group — clearMarkers() is O(1) on this group
                .append("var markerLayer = L.layerGroup().addTo(map);")

                // ── SYSTEC fix: invalidateSize after the container is painted ────
                // The 300 ms delay gives JavaFX time to finish sizing the WebView
                // before Leaflet measures it. The resize listener keeps it correct
                // when the user drags the window.
                .append("setTimeout(function() { map.invalidateSize(); }, 300);")
                .append("window.addEventListener('resize', function() { map.invalidateSize(); });")

                // ── Public API called by Java via executeScript() ────────────────
                .append("function clearMarkers() {")
                .append("  markerLayer.clearLayers();")
                .append("}")

                .append("function addMarker(lat, lng, color, popupHtml) {")
                .append("  var icon = L.divIcon({")
                .append("    className: '',")
                .append("    html: '<div style=\"'")
                .append("         + 'width:16px;height:16px;'")
                .append("         + 'border-radius:50%;'")
                .append("         + 'background:' + color + ';'")
                .append("         + 'border:2px solid #ffffff;'")
                .append("         + 'box-shadow:0 1px 6px rgba(0,0,0,0.50);'")
                .append("         + '\">'")
                .append("         + '</div>',")
                .append("    iconSize:   [16, 16],")
                .append("    iconAnchor: [8, 8]")
                .append("  });")
                .append("  L.marker([lat, lng], { icon: icon })")
                .append("   .bindPopup(popupHtml)")
                .append("   .addTo(markerLayer);")
                .append("}")

                .append("</script>")
                .append("</body>")
                .append("</html>");

        return h.toString();
    }

    // ── Utility ───────────────────────────────────────────────────────────

    /** Returns a hex colour that matches the disaster type. */
    private String colorFor(String type) {
        if (type == null) return "#6b7280";
        return switch (type) {
            case "Flood"      -> "#3b82f6";   // blue
            case "Fire"       -> "#ef4444";   // red
            case "Earthquake" -> "#f97316";   // orange
            case "Typhoon"    -> "#8b5cf6";   // purple
            case "Landslide"  -> "#a16207";   // brown
            default           -> "#6b7280";   // grey
        };
    }

    /**
     * Escapes a string so it is safe to embed inside a single-quoted JS string.
     * Handles backslashes, single quotes, and line breaks.
     */
    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'",  "\\'")
                .replace("\n", " ")
                .replace("\r", "");
    }
}