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
    private boolean        mapReady      = false;   // true once Leaflet is loaded
    private List<Incident> pendingRender = null;    // markers queued before map was ready

    // ── Lifecycle ─────────────────────────────────────────────────────────
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);
        engine.setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36"
        );

        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        typeFilterCombo.getSelectionModel().selectFirst();

        // Listen for when the page finishes loading, then mark map as ready
        // and flush any markers that arrived before the page was ready.
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapReady = true;
                if (pendingRender != null) {
                    pushMarkers(pendingRender);
                    pendingRender = null;
                }
            }
        });

        // Load the base HTML ONCE — never call loadContent() again after this.
        engine.loadContent(buildBaseHtml(), "text/html");
    }

    /** Called from MainController after the view is loaded. */
    public void loadIncidents(List<Incident> incidents) {
        this.allIncidents = incidents;
        renderMarkers(incidents);
    }

    // ── Handlers ──────────────────────────────────────────────────────────
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
                : incidents.size() + " incidents loaded.");

        if (!mapReady) {
            // Page hasn't finished loading yet — save for when it does
            pendingRender = incidents;
        } else {
            pushMarkers(incidents);
        }
    }

    /**
     * Pushes markers into the already-running Leaflet map via executeScript().
     * This is the key fix — the page is never reloaded so:
     *   • Tiles stay cached in memory (no re-download, no choppiness)
     *   • Leaflet's internal state is preserved
     *   • Only the marker layer is cleared and redrawn
     */
    private void pushMarkers(List<Incident> incidents) {
        StringBuilder js = new StringBuilder();

        // clearMarkers() is defined in the base HTML — removes all existing markers
        js.append("clearMarkers();");

        for (Incident inc : incidents) {
            double lat = inc.getLatitude();
            double lng = inc.getLongitude();
            if (lat == 0.0 && lng == 0.0) continue;

            String color = colorFor(inc.getType());
            String popup = escapeJs(
                    inc.getType() + " \u2014 " + inc.getLocation()
                            + (inc.getDescription() != null && !inc.getDescription().isEmpty()
                            ? "<br>" + inc.getDescription() : "")
                            + "<br><b>Status:</b> " + inc.getStatus()
                            + "<br><b>Date:</b> "   + inc.getDateString()
            );

            js.append("addMarker(")
                    .append(lat).append(",")
                    .append(lng).append(",'")
                    .append(color).append("','")
                    .append(popup).append("');");
        }

        engine.executeScript(js.toString());
    }

    /**
     * Builds the base HTML page that is loaded exactly once.
     *
     * The page exposes two JS functions that Java calls via executeScript():
     *   clearMarkers()           — removes all markers from the map
     *   addMarker(lat,lng,color,popup) — adds one coloured dot marker
     *
     * Plain StringBuilder is used (no String.format / formatted) to avoid
     * UnknownFormatConversionException from % characters in URLs.
     */
    private String buildBaseHtml() {
        StringBuilder html = new StringBuilder();

        double centerLat = 12.8797;
        double centerLng = 121.7740;
        int    zoom      = 6;

        html.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<head>")
                .append("<meta charset='utf-8'/>")
                .append("<title>Disaster Map</title>")
                .append("<link rel='stylesheet'")
                .append("  href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'")
                .append("  crossorigin=''/>")
                .append("<style>")
                .append("  * { margin:0; padding:0; box-sizing:border-box; }")
                // html + body + #map must all be 100% / 100vh with no overflow.
                // This is what prevents the tile-grid / choppy appearance on resize.
                .append("  html, body { width:100%; height:100%; overflow:hidden; }")
                .append("  #map { width:100vw; height:100vh; }")
                .append("</style>")
                .append("</head>")
                .append("<body>")
                .append("<div id='map'></div>")
                .append("<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'")
                .append("        crossorigin=''></script>")
                .append("<script>")

                // Create the map
                .append("var map = L.map('map',{")
                .append("  center:[").append(centerLat).append(",").append(centerLng).append("],")
                .append("  zoom:").append(zoom).append(",")
                .append("  zoomControl:true,")
                .append("  scrollWheelZoom:true")
                .append("});")

                // OSM tile layer
                .append("L.tileLayer(")
                .append("  'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',")
                .append("  {attribution:'&copy; OpenStreetMap contributors',maxZoom:19}")
                .append(").addTo(map);")

                // Layer group that holds all incident markers
                // Storing them in a group makes clearMarkers() O(1)
                .append("var markerLayer = L.layerGroup().addTo(map);")

                // invalidateSize fixes the tile-fragmentation bug on first render.
                // The 300ms delay gives JavaFX time to finish sizing the WebView
                // before Leaflet measures it.
                .append("setTimeout(function(){ map.invalidateSize(); }, 300);")
                .append("window.addEventListener('resize', function(){ map.invalidateSize(); });")

                // Public API called by Java via executeScript()
                .append("function clearMarkers() { markerLayer.clearLayers(); }")

                .append("function addMarker(lat, lng, color, popupHtml) {")
                .append("  var icon = L.divIcon({")
                .append("    className:'',")
                .append("    html:'<div style=\"'")
                .append("        +'width:16px;height:16px;'")
                .append("        +'border-radius:50%;'")
                .append("        +'background:'+color+';'")
                .append("        +'border:2px solid white;'")
                .append("        +'box-shadow:0 1px 5px rgba(0,0,0,.45);'")
                .append("        +'\">'+'</div>',")
                .append("    iconSize:[16,16],")
                .append("    iconAnchor:[8,8]")
                .append("  });")
                .append("  L.marker([lat,lng],{icon:icon})")
                .append("   .bindPopup(popupHtml)")
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

    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'",  "\\'")
                .replace("\n", " ")
                .replace("\r", "");
    }
}