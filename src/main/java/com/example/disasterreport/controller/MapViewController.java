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
 * All four map-related bugs are fixed here:
 *
 * FIX A — Markers not showing
 *   Root cause: ReportIncidentController was saving 0.0 / 0.0 because it
 *   never read the latitude/longitude fields.  That fix is in
 *   ReportIncidentController.  This controller already skips 0/0 incidents
 *   in pushMarkers(), which is correct — zero-coordinate records are simply
 *   not pinned.  A debug counter in the status bar now shows how many valid
 *   markers exist so testers can confirm data is reaching the map.
 *
 * FIX B — Map choppy / blank tiles
 *   • The HTML page is loaded exactly ONCE in initialize().
 *   • invalidateSize() is called 300 ms after load so Leaflet can measure
 *     the WebView correctly before drawing tiles.
 *   • A ResizeObserver inside the page calls invalidateSize() whenever the
 *     container changes, preventing tile-grid fragmentation on window resize.
 *   • pushMarkers() only clears the markerLayer, never the whole map, so
 *     the tile cache stays warm.
 *
 * FIX C — Map not centering on user location
 *   • The Geolocation API call is now deferred 600 ms (was 400 ms) to give
 *     the WebKit engine enough time to initialise its permission subsystem.
 *   • On success: map.flyTo() with a 1.5-second animation, plus a pulsing
 *     "You are here" marker with a rich popup.
 *   • On denial / error: silent fallback to Philippines centre — no alert,
 *     no broken state.
 *
 * FIX D — Marker injection timing
 *   • loadIncidents() queues the incident list if the page hasn't finished
 *     loading yet (pendingRender).  The SUCCEEDED state listener flushes
 *     the queue, so markers are always injected at the right moment.
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
    private boolean        mapReady      = false;
    private List<Incident> pendingRender = null;   // queued before map was ready

    // ── Lifecycle ──────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        engine = mapWebView.getEngine();
        engine.setJavaScriptEnabled(true);

        // A modern UA prevents WebKit from refusing Leaflet CDN resources
        engine.setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/120.0.0.0 Safari/537.36"
        );

        // Filter combo
        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        typeFilterCombo.getSelectionModel().selectFirst();

        // ── State listener — flush queue on load success ───────────────────
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {

            if (newState == Worker.State.SUCCEEDED) {
                mapReady = true;
                statusLabel.setText("Map loaded. Detecting your location…");

                if (pendingRender != null) {
                    pushMarkers(pendingRender);
                    pendingRender = null;
                }

            } else if (newState == Worker.State.FAILED) {
                statusLabel.setText("Map failed to load — check your internet connection.");
            }
        });

        // ── Load the base HTML exactly once ────────────────────────────────
        engine.loadContent(buildBaseHtml(), "text/html");
    }

    /**
     * Entry-point called by MainController after the view node is attached.
     * Thread-safe: uses Platform.runLater if called from a non-FX thread.
     */
    public void loadIncidents(List<Incident> incidents) {
        this.allIncidents = new ArrayList<>(incidents);
        renderMarkers(incidents);
    }

    // ── FXML handlers ──────────────────────────────────────────────────────

    @FXML private void handleShowAll()  { typeFilterCombo.getSelectionModel().select("All"); renderMarkers(allIncidents); }
    @FXML private void handleRefresh()  { renderMarkers(getFilteredList()); }
    @FXML private void handleFilter()   { renderMarkers(getFilteredList()); }

    // ── Private helpers ────────────────────────────────────────────────────

    private List<Incident> getFilteredList() {
        String sel = typeFilterCombo.getValue();
        if (sel == null || sel.equals("All")) return allIncidents;
        return allIncidents.stream()
                .filter(i -> sel.equals(i.getType()))
                .toList();
    }

    /** Schedules a marker push, queuing it if the page isn't ready yet. */
    private void renderMarkers(List<Incident> incidents) {
        Runnable action = () -> {
            long validCount = incidents.stream()
                    .filter(i -> i.getLatitude() != 0.0 || i.getLongitude() != 0.0)
                    .count();

            markerCountLabel.setText(validCount + " marker" + (validCount == 1 ? "" : "s"));
            statusLabel.setText(validCount == 0
                    ? "No geotagged incidents to display. Enter lat/lng when reporting."
                    : validCount + " incident" + (validCount == 1 ? "" : "s") + " on map.");

            if (!mapReady) {
                pendingRender = incidents;   // will be flushed by the SUCCEEDED listener
            } else {
                pushMarkers(incidents);
            }
        };

        if (Platform.isFxApplicationThread()) action.run();
        else Platform.runLater(action);
    }

    /**
     * Clears only the marker layer and redraws it — the tile cache is never
     * touched, keeping map performance smooth (FIX B / FIX D).
     */
    private void pushMarkers(List<Incident> incidents) {
        StringBuilder js = new StringBuilder();
        js.append("clearMarkers();");

        for (Incident inc : incidents) {
            double lat = inc.getLatitude();
            double lng = inc.getLongitude();
            if (lat == 0.0 && lng == 0.0) continue;   // skip untagged incidents

            String color      = colorFor(inc.getType());
            String pulseColor = pulseColorFor(inc.getType());
            String icon       = emojiFor(inc.getType());

            String descHtml = (inc.getDescription() != null && !inc.getDescription().isBlank())
                    ? "<p style='margin:6px 0 0;font-size:12px;color:#374151'>"
                      + escapeJs(inc.getDescription()) + "</p>"
                    : "";

            String popupHtml = escapeJs(
                    "<div style='font-family:sans-serif;min-width:210px;padding:2px'>"
                            + "<div style='display:flex;align-items:center;gap:6px;margin-bottom:6px'>"
                            + "  <span style='font-size:20px'>" + icon + "</span>"
                            + "  <strong style='font-size:14px;color:#1a2a4a'>" + inc.getType() + "</strong>"
                            + "</div>"
                            + "<p style='margin:2px 0;font-size:12px;color:#6b7280'>📍 " + inc.getLocation() + "</p>"
                            + "<p style='margin:4px 0;font-size:12px'>"
                            + "  <span style='background:" + color + ";color:white;"
                            + "    padding:2px 8px;border-radius:10px;font-size:11px'>"
                            + inc.getStatus() + "</span>"
                            + "  &nbsp;|&nbsp;"
                            + "  <span style='font-size:11px;color:#9ca3af'>" + inc.getSeverity() + "</span>"
                            + "</p>"
                            + "<p style='margin:4px 0 0;font-size:11px;color:#9ca3af'>📅 " + inc.getDateString() + "</p>"
                            + descHtml
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
     * Builds the full HTML page that is loaded exactly once in initialize().
     *
     * Key design decisions
     * ─────────────────────
     * • html / body / #map → width:100%; height:100vh; overflow:hidden
     *   Prevents the "tile-grid" clipping bug when the window is resized.
     * • invalidateSize() is called 300 ms after load AND on every resize via
     *   ResizeObserver — keeps the canvas correctly sized without ever
     *   reloading the whole page.
     * • Geolocation deferred 600 ms — WebKit needs a moment before the
     *   permission system is ready.
     * • clearMarkers() / addMarker() are the only entry-points Java calls;
     *   they operate on a LayerGroup, never on the map itself, so the tile
     *   cache is never flushed.
     */
    private String buildBaseHtml() {
        final double DEFAULT_LAT  = 12.8797;   // Philippines centre
        final double DEFAULT_LNG  = 121.7740;
        final int    DEFAULT_ZOOM = 6;

        // Use StringBuilder to avoid String.format() choking on % in tile URLs
        StringBuilder h = new StringBuilder();

        h.append("<!DOCTYPE html><html>")
                .append("<head>")
                .append("<meta charset='utf-8'/>")
                .append("<meta name='viewport' content='width=device-width,initial-scale=1'/>")
                .append("<title>Disaster Map</title>")

                // Leaflet CSS
                .append("<link rel='stylesheet' crossorigin=''")
                .append(" href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>")

                // Page styles
                .append("<style>")
                .append("  *{margin:0;padding:0;box-sizing:border-box}")
                // 100% / 100vh + overflow:hidden prevents tile-fragmentation (FIX B)
                .append("  html,body{width:100%;height:100%;overflow:hidden;background:#e5e7eb}")
                .append("  #map{width:100%;height:100vh}")

                // "You are here" pulsing dot (FIX C)
                .append("  @keyframes pulse{")
                .append("    0%{transform:scale(1);opacity:1}")
                .append("    70%{transform:scale(2.6);opacity:0}")
                .append("    100%{transform:scale(1);opacity:0}")
                .append("  }")
                .append("  .user-dot{")
                .append("    width:14px;height:14px;border-radius:50%;")
                .append("    background:#2563eb;border:2.5px solid white;")
                .append("    box-shadow:0 0 0 0 rgba(37,99,235,.6);")
                .append("    position:relative;")
                .append("  }")
                .append("  .user-dot::after{")
                .append("    content:'';position:absolute;top:-5px;left:-5px;")
                .append("    width:24px;height:24px;border-radius:50%;")
                .append("    background:rgba(37,99,235,.35);")
                .append("    animation:pulse 1.8s ease-out infinite;")
                .append("  }")

                // Prettier Leaflet popups
                .append("  .leaflet-popup-content-wrapper{border-radius:10px;box-shadow:0 4px 20px rgba(0,0,0,.18)}")
                .append("  .leaflet-popup-content{margin:14px 16px}")
                .append("</style>")
                .append("</head>")
                .append("<body>")
                .append("<div id='map'></div>")

                // Leaflet JS
                .append("<script crossorigin=''")
                .append(" src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>")

                .append("<script>")

                // ── Create map once ───────────────────────────────────────────────
                .append("var map=L.map('map',{")
                .append("  center:[").append(DEFAULT_LAT).append(",").append(DEFAULT_LNG).append("],")
                .append("  zoom:").append(DEFAULT_ZOOM).append(",")
                .append("  zoomControl:true,")
                .append("  scrollWheelZoom:true")
                .append("});")

                // ── OSM tile layer ────────────────────────────────────────────────
                .append("L.tileLayer(")
                .append("  'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{")
                .append("  attribution:'&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>',")
                .append("  maxZoom:19,")
                .append("  keepBuffer:4")    // keep more tiles in memory → smoother pan (FIX B)
                .append("}).addTo(map);")

                // ── Marker layer (clearLayers is O(1) → smooth refresh) ───────────
                .append("var markerLayer=L.layerGroup().addTo(map);")

                // ── Fix tile fragmentation: call invalidateSize after load ─────────
                // FIX B: 300 ms delay lets JavaFX finish sizing the WebView
                .append("setTimeout(function(){map.invalidateSize();},300);")

                // Also recalculate on every container resize (FIX B)
                .append("if(typeof ResizeObserver!=='undefined'){")
                .append("  new ResizeObserver(function(){map.invalidateSize();}).observe(document.getElementById('map'));")
                .append("}else{")
                .append("  window.addEventListener('resize',function(){map.invalidateSize();});")
                .append("}")

                // ── Geolocation → "You are here" marker (FIX C) ──────────────────
                .append("var userMarker=null;")
                .append("function locateUser(){")
                .append("  if(!navigator.geolocation){")
                .append("    console.warn('Geolocation API not available.');return;")
                .append("  }")
                .append("  navigator.geolocation.getCurrentPosition(")
                .append("    function(pos){")                              // SUCCESS
                .append("      var lat=pos.coords.latitude,lng=pos.coords.longitude;")
                .append("      var acc=pos.coords.accuracy;")
                .append("      map.flyTo([lat,lng],13,{animate:true,duration:1.5});")
                // Pulsing dot icon
                .append("      var dotIcon=L.divIcon({className:'',iconSize:[14,14],iconAnchor:[7,7],")
                .append("        html:'<div class=\"user-dot\"></div>'});")
                .append("      if(userMarker){userMarker.remove();}")
                .append("      userMarker=L.marker([lat,lng],{icon:dotIcon,zIndexOffset:1000})")
                .append("        .bindPopup(")
                .append("          '<b style=\"color:#1a2a4a\">📍 You are here</b>'")
                .append("          +'<br><small style=\"color:#6b7280\">Lat: '+lat.toFixed(5)+'</small>'")
                .append("          +'<br><small style=\"color:#6b7280\">Lng: '+lng.toFixed(5)+'</small>'")
                .append("          +'<br><small style=\"color:#9ca3af\">Accuracy: ~'+Math.round(acc)+'m</small>',")
                .append("          {maxWidth:200}")
                .append("        )")
                .append("        .addTo(map);")
                .append("    },")
                .append("    function(err){")                             // DENIED / ERROR
                .append("      console.warn('Geolocation denied:',err.message);")
                .append("      /* silent fallback — map stays at Philippines centre */")
                .append("    },")
                .append("    {enableHighAccuracy:true,timeout:12000,maximumAge:60000}")
                .append("  );")
                .append("}")
                // 600 ms delay: WebKit needs time to init its permission subsystem (FIX C)
                .append("setTimeout(locateUser,600);")

                // ── Public API called by Java executeScript() ─────────────────────

                // clearMarkers — removes ALL incident pins; never touches the user marker
                .append("function clearMarkers(){markerLayer.clearLayers();}")

                // addMarker(lat, lng, color, pulseColor, popupHtml)
                .append("function addMarker(lat,lng,color,pulseColor,popupHtml){")
                .append("  var iconHtml=")
                .append("    '<div style=\"'")
                .append("    +'width:20px;height:20px;'")
                .append("    +'border-radius:50%;'")
                .append("    +'background:'+color+';'")
                .append("    +'border:2.5px solid white;'")
                .append("    +'box-shadow:0 2px 8px rgba(0,0,0,.45);'")
                .append("    +'cursor:pointer;transition:transform .15s'")
                .append("    +'\" onmouseover=\"this.style.transform=\\'scale(1.3)\\'\"'")
                .append("    +' onmouseout=\"this.style.transform=\\'scale(1)\\'\">'")
                .append("    +'</div>';")
                .append("  var icon=L.divIcon({className:'',html:iconHtml,iconSize:[20,20],iconAnchor:[10,10]});")
                .append("  L.marker([lat,lng],{icon:icon})")
                .append("   .bindPopup(popupHtml,{maxWidth:270,closeButton:true})")
                .append("   .addTo(markerLayer);")
                .append("}")

                .append("</script>")
                .append("</body></html>");

        return h.toString();
    }

    // ── Color / emoji helpers ──────────────────────────────────────────────

    private String colorFor(String type) {
        if (type == null) return "#6b7280";
        return switch (type) {
            case "Flood"      -> "#3b82f6";
            case "Fire"       -> "#ef4444";
            case "Earthquake" -> "#f97316";
            case "Typhoon"    -> "#8b5cf6";
            case "Landslide"  -> "#92400e";
            default           -> "#6b7280";
        };
    }

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

    /** Makes a Java string safe to embed inside a single-quoted JS string. */
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