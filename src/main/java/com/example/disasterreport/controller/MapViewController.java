package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import javafx.application.Platform;
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

/**
 * MapViewController — drives the Leaflet map embedded in a JavaFX WebView.
 *
 * Task 1  → Color-coded markers per incident type; rich popup (type, location,
 *            status, date, description).
 * Task 2  → Browser Geolocation API detects the user's position, centres the
 *            map, and drops a "You are here" pulse marker.  Falls back to the
 *            Philippines centre if the user denies permission or the browser
 *            API is unavailable.
 * Task 3  → Performance: the HTML page is loaded exactly ONCE.  Subsequent
 *            filter/refresh calls only inject new marker data via
 *            executeScript() so the tile cache is never flushed and Leaflet's
 *            internal state is preserved.  invalidateSize() is called once
 *            after load to fix the tile-fragmentation bug.
 */
public class MapViewController implements Initializable {

    // ── FXML bindings ──────────────────────────────────────────────────────
    @FXML private WebView          mapWebView;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private Label            markerCountLabel;
    @FXML private Label            statusLabel;

    // ── State ──────────────────────────────────────────────────────────────
    private WebEngine      engine;
    private List<Incident> allIncidents  = new ArrayList<>();

    /**
     * true once the Leaflet page has finished loading (Worker.State.SUCCEEDED).
     * Guarded by the JavaFX application thread.
     */
    private boolean        mapReady      = false;

    /**
     * Markers queued before the map was ready — flushed on SUCCEEDED.
     * null means "nothing pending".
     */
    private List<Incident> pendingRender = null;

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);

        /*
         * A modern user-agent is required so that the Leaflet CDN and the
         * browser Geolocation API are served / accepted correctly by WebKit.
         */
        engine.setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36"
        );

        // Populate filter combo
        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        typeFilterCombo.getSelectionModel().selectFirst();

        /*
         * Performance (Task 3): listen for the single load event.
         * When SUCCEEDED, mark the map ready and flush any queued markers.
         * We never call loadContent() again after this point.
         */
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapReady = true;
                statusLabel.setText("Map loaded. Detecting your location…");

                if (pendingRender != null) {
                    pushMarkers(pendingRender);
                    pendingRender = null;
                }
            } else if (newState == Worker.State.FAILED) {
                statusLabel.setText("Map failed to load. Check your internet connection.");
            }
        });

        // ── Load the base HTML exactly once ────────────────────────────────
        engine.loadContent(buildBaseHtml(), "text/html");
    }

    /**
     * Entry-point called by MainController after the view node is attached.
     * Safe to call before the page has finished loading — markers will be
     * queued and flushed once SUCCEEDED fires.
     */
    public void loadIncidents(List<Incident> incidents) {
        this.allIncidents = new ArrayList<>(incidents);
        renderMarkers(incidents);
    }

    // ── FXML action handlers ───────────────────────────────────────────────

    @FXML
    private void handleShowAll() {
        typeFilterCombo.getSelectionModel().select("All");
        renderMarkers(allIncidents);
    }

    @FXML
    private void handleRefresh() {
        renderMarkers(getFilteredList());
    }

    @FXML
    private void handleFilter() {
        renderMarkers(getFilteredList());
    }

    // ── Private helpers ────────────────────────────────────────────────────

    private List<Incident> getFilteredList() {
        String sel = typeFilterCombo.getValue();
        if (sel == null || sel.equals("All")) return allIncidents;
        return allIncidents.stream()
                .filter(i -> sel.equals(i.getType()))
                .toList();
    }

    private void renderMarkers(List<Incident> incidents) {
        // Always run on the JavaFX application thread
        Runnable action = () -> {
            long validCount = incidents.stream()
                    .filter(i -> i.getLatitude() != 0.0 || i.getLongitude() != 0.0)
                    .count();

            markerCountLabel.setText(validCount + " markers");
            statusLabel.setText(incidents.isEmpty()
                    ? "No incidents to display."
                    : validCount + " incident" + (validCount == 1 ? "" : "s") + " on map.");

            if (!mapReady) {
                pendingRender = incidents;   // queue; will flush on SUCCEEDED
            } else {
                pushMarkers(incidents);
            }
        };

        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    /**
     * Task 1 & Task 3 — pushes marker data into the running Leaflet map by
     * calling the JavaScript helper functions defined in the base HTML.
     *
     * The page is NEVER reloaded here, so:
     *   • OSM tile cache stays warm  → no choppy re-download
     *   • Leaflet's internal state is preserved  → smooth pan/zoom
     *   • Only the marker layer is cleared and redrawn  → O(1) clear
     */
    private void pushMarkers(List<Incident> incidents) {
        StringBuilder js = new StringBuilder();

        // clearMarkers() removes all markers from markerLayer
        js.append("clearMarkers();");

        for (Incident inc : incidents) {
            double lat = inc.getLatitude();
            double lng = inc.getLongitude();

            // Skip incidents without coordinates
            if (lat == 0.0 && lng == 0.0) continue;

            String color       = colorFor(inc.getType());
            String pulseColor  = pulseColorFor(inc.getType());
            String icon        = emojiFor(inc.getType());

            // Build the popup HTML — newlines replaced with spaces for JS string safety
            String description = (inc.getDescription() != null && !inc.getDescription().isBlank())
                    ? "<p style='margin:4px 0 0;color:#374151'>" + escapeJs(inc.getDescription()) + "</p>"
                    : "";

            String popupHtml = escapeJs(
                    "<div style='font-family:sans-serif;min-width:200px'>"
                            + "<div style='display:flex;align-items:center;gap:6px;margin-bottom:6px'>"
                            + "  <span style='font-size:18px'>" + icon + "</span>"
                            + "  <strong style='font-size:14px;color:#1a2a4a'>" + inc.getType() + "</strong>"
                            + "</div>"
                            + "<p style='margin:2px 0;font-size:12px;color:#6b7280'>📍 " + inc.getLocation() + "</p>"
                            + "<p style='margin:2px 0;font-size:12px'>"
                            +   "<span style='background:" + color + ";color:white;padding:1px 7px;"
                            +   "border-radius:10px;font-size:11px'>" + inc.getStatus() + "</span>"
                            + "</p>"
                            + "<p style='margin:4px 0 0;font-size:11px;color:#9ca3af'>📅 " + inc.getDateString() + "</p>"
                            + description
                            + "</div>"
            );

            js.append("addMarker(")
                    .append(lat).append(",")
                    .append(lng).append(",'")
                    .append(color).append("','")
                    .append(pulseColor).append("','")
                    .append(popupHtml).append("');");
        }

        engine.executeScript(js.toString());
    }

    // ── HTML builder ───────────────────────────────────────────────────────

    /**
     * Builds the complete HTML page that is loaded exactly once.
     *
     * Task 2 — Geolocation:
     *   JavaScript calls navigator.geolocation.getCurrentPosition().
     *   On success → map flies to user's location, adds a pulsing "You are here" marker.
     *   On failure → map stays at the Philippines default centre.
     *
     * Task 3 — Performance:
     *   • html/body/#map are 100 %/100vh with overflow:hidden → no tile-grid chop.
     *   • invalidateSize() is called once after a 300 ms delay to let JavaFX
     *     finish sizing the WebView before Leaflet measures it.
     *   • A resize listener calls invalidateSize() whenever the window changes.
     *
     * Plain StringBuilder (no String.format) to avoid
     * UnknownFormatConversionException on % chars in tile URLs.
     */
    private String buildBaseHtml() {
        // Default centre: Philippines
        final double DEFAULT_LAT  = 12.8797;
        final double DEFAULT_LNG  = 121.7740;
        final int    DEFAULT_ZOOM = 6;

        StringBuilder h = new StringBuilder();

        h.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<head>")
                .append("<meta charset='utf-8'/>")
                .append("<meta name='viewport' content='width=device-width,initial-scale=1'/>")
                .append("<title>Disaster Map</title>")

                // ── Leaflet CSS ────────────────────────────────────────────────
                .append("<link rel='stylesheet' crossorigin=''")
                .append(" href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>")

                // ── Page styles ────────────────────────────────────────────────
                .append("<style>")
                // Task 3: 100%/100vh with no overflow prevents tile-fragmentation
                .append("  *{margin:0;padding:0;box-sizing:border-box}")
                .append("  html,body{width:100%;height:100%;overflow:hidden}")
                .append("  #map{width:100vw;height:100vh}")
                // "You are here" pulsing ring animation (Task 2)
                .append("  @keyframes pulse{")
                .append("    0%{transform:scale(1);opacity:1}")
                .append("    70%{transform:scale(2.4);opacity:0}")
                .append("    100%{transform:scale(1);opacity:0}")
                .append("  }")
                .append("  .user-dot{")
                .append("    width:14px;height:14px;border-radius:50%;")
                .append("    background:#2563eb;border:2px solid white;")
                .append("    box-shadow:0 0 0 0 rgba(37,99,235,.6);")
                .append("    position:relative;")
                .append("  }")
                .append("  .user-dot::after{")
                .append("    content:'';position:absolute;top:-4px;left:-4px;")
                .append("    width:22px;height:22px;border-radius:50%;")
                .append("    background:rgba(37,99,235,.35);")
                .append("    animation:pulse 1.8s ease-out infinite;")
                .append("  }")
                .append("</style>")
                .append("</head>")
                .append("<body>")
                .append("<div id='map'></div>")

                // ── Leaflet JS ─────────────────────────────────────────────────
                .append("<script crossorigin=''")
                .append(" src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>")

                .append("<script>")

                // ── Create map (Task 3: single creation, never recreated) ──────
                .append("var map=L.map('map',{")
                .append("  center:[").append(DEFAULT_LAT).append(",").append(DEFAULT_LNG).append("],")
                .append("  zoom:").append(DEFAULT_ZOOM).append(",")
                .append("  zoomControl:true,")
                .append("  scrollWheelZoom:true")
                .append("});")

                // ── OSM tile layer ─────────────────────────────────────────────
                .append("L.tileLayer(")
                .append("  'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',")
                .append("  {attribution:'&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors',maxZoom:19}")
                .append(").addTo(map);")

                // ── Marker layer (Task 3: clearLayers() is O(1)) ──────────────
                .append("var markerLayer=L.layerGroup().addTo(map);")

                // ── Task 3: fix tile-fragmentation on first render ─────────────
                // 300 ms delay gives JavaFX time to finish sizing the WebView.
                .append("setTimeout(function(){map.invalidateSize();},300);")
                .append("window.addEventListener('resize',function(){map.invalidateSize();});")

                // ── Task 2: Geolocation → "You are here" marker ───────────────
                .append("var userMarker=null;")
                .append("function locateUser(){")
                .append("  if(!navigator.geolocation){return;}")   // API not available → stay at default
                .append("  navigator.geolocation.getCurrentPosition(")
                .append("    function(pos){")
                .append("      var lat=pos.coords.latitude, lng=pos.coords.longitude;")
                .append("      map.flyTo([lat,lng],13,{animate:true,duration:1.5});")
                // Pulsing "You are here" dot icon
                .append("      var icon=L.divIcon({className:'',iconSize:[14,14],iconAnchor:[7,7],")
                .append("        html:'<div class=\"user-dot\"></div>'});")
                .append("      if(userMarker){userMarker.remove();}")
                .append("      userMarker=L.marker([lat,lng],{icon:icon,zIndexOffset:1000})")
                .append("        .bindPopup('<b>📍 You are here</b><br><small>Lat: '+lat.toFixed(5)+'<br>Lng: '+lng.toFixed(5)+'</small>',{maxWidth:200})")
                .append("        .addTo(map);")
                .append("    },")
                // On denial / error → stay at Philippines default (no alert, silent fallback)
                .append("    function(err){console.warn('Geolocation denied:',err.message);},")
                .append("    {enableHighAccuracy:true,timeout:10000,maximumAge:60000}")
                .append("  );")
                .append("}")
                // Trigger geolocation after the map tiles start loading
                .append("setTimeout(locateUser,400);")

                // ── Task 1: Public API called by Java via executeScript() ──────

                // clearMarkers() — removes all incident markers (NOT the user marker)
                .append("function clearMarkers(){markerLayer.clearLayers();}")

                // addMarker(lat, lng, color, pulseColor, popupHtml)
                // Creates a styled circular div-icon with the incident's type color.
                .append("function addMarker(lat,lng,color,pulseColor,popupHtml){")
                .append("  var html=")
                .append("    '<div style=\"'")
                .append("    +'width:18px;height:18px;'")
                .append("    +'border-radius:50%;'")
                .append("    +'background:'+color+';'")
                .append("    +'border:2.5px solid white;'")
                .append("    +'box-shadow:0 2px 6px rgba(0,0,0,.45);'")
                .append("    +'cursor:pointer;'")
                .append("    +'\">'")
                .append("    +'</div>';")
                .append("  var icon=L.divIcon({className:'',html:html,iconSize:[18,18],iconAnchor:[9,9]});")
                .append("  L.marker([lat,lng],{icon:icon})")
                .append("   .bindPopup(popupHtml,{maxWidth:260,className:'disaster-popup'})")
                .append("   .addTo(markerLayer);")
                .append("}")

                .append("</script>")
                .append("</body>")
                .append("</html>");

        return h.toString();
    }

    // ── Color helpers (Task 1) ─────────────────────────────────────────────

    /** Returns the solid fill color for a given incident type. */
    private String colorFor(String type) {
        if (type == null) return "#6b7280";
        return switch (type) {
            case "Flood"      -> "#3b82f6";   // blue
            case "Fire"       -> "#ef4444";   // red
            case "Earthquake" -> "#f97316";   // orange
            case "Typhoon"    -> "#8b5cf6";   // purple
            case "Landslide"  -> "#92400e";   // brown
            default           -> "#6b7280";   // grey  (Other / unknown)
        };
    }

    /** Semi-transparent version used for future pulse ring effects. */
    private String pulseColorFor(String type) {
        if (type == null) return "rgba(107,114,128,.35)";
        return switch (type) {
            case "Flood"      -> "rgba(59,130,246,.35)";
            case "Fire"       -> "rgba(239,68,68,.35)";
            case "Earthquake" -> "rgba(249,115,22,.35)";
            case "Typhoon"    -> "rgba(139,92,246,.35)";
            case "Landslide"  -> "rgba(146,64,14,.35)";
            default           -> "rgba(107,114,128,.35)";
        };
    }

    /** Returns an emoji icon that visually represents the disaster type. */
    private String emojiFor(String type) {
        if (type == null) return "⚠️";
        return switch (type) {
            case "Flood"      -> "🌊";
            case "Fire"       -> "🔥";
            case "Earthquake" -> "🌍";
            case "Typhoon"    -> "🌀";
            case "Landslide"  -> "⛰️";
            default           -> "⚠️";
        };
    }

    // ── String escaping ────────────────────────────────────────────────────

    /**
     * Escapes a Java string so it is safe to embed inside a single-quoted
     * JavaScript string literal passed via executeScript().
     */
    private String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'",  "\\'")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", "")
                .replace("\t", " ");
    }
}