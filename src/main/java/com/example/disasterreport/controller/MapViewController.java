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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;

public class MapViewController implements Initializable {

    @FXML private WebView          mapWebView;
    @FXML private ComboBox<String> typeFilterCombo;
    @FXML private Label            markerCountLabel;
    @FXML private Label            statusLabel;

    private WebEngine      engine;
    private List<Incident> allIncidents  = new ArrayList<>();
    private boolean        mapReady      = false;
    private List<Incident> pendingRender = null;
    private Path           tempDir       = null;

    // Center of the Philippines
    private static final double CENTER_LAT = 12.8797;
    private static final double CENTER_LNG = 121.7740;
    private static final int    ZOOM       = 6;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        engine = mapWebView.getEngine();
        mapWebView.setContextMenuEnabled(false);
        engine.setJavaScriptEnabled(true);

        // Standard browser user agent — OSM tiles require this
        engine.setUserAgent(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/124.0.0.0 Safari/537.36"
        );

        typeFilterCombo.setItems(FXCollections.observableArrayList(
                "All", "Flood", "Fire", "Earthquake", "Typhoon", "Landslide", "Other"
        ));
        typeFilterCombo.getSelectionModel().selectFirst();

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                mapReady = true;

                // Single delayed fixMap — too many calls cause jitter
                Platform.runLater(() -> {
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    engine.executeScript("map.invalidateSize();");
                });

                if (pendingRender != null) {
                    List<Incident> toRender = pendingRender;
                    pendingRender = null;
                    Platform.runLater(() -> pushMarkers(toRender));
                }
            }
            if (newState == Worker.State.FAILED) {
                statusLabel.setText("Map failed to load.");
                System.err.println("[MapView] Page load FAILED: "
                        + engine.getLoadWorker().getException());
            }
        });

        engine.setOnAlert(e -> System.err.println("[MapView JS] " + e.getData()));

        try {
            loadMapPage();
        } catch (IOException e) {
            statusLabel.setText("Could not prepare map files: " + e.getMessage());
            e.printStackTrace();
        }
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

    private void loadMapPage() throws IOException {
        tempDir = Files.createTempDirectory("disastermap_");
        tempDir.toFile().deleteOnExit();

        copyResource("/com/example/disasterreport/leaflet/leaflet.css",
                tempDir.resolve("leaflet.css"));
        copyResource("/com/example/disasterreport/leaflet/leaflet.js",
                tempDir.resolve("leaflet.js"));

        Path htmlFile = tempDir.resolve("map.html");
        Files.writeString(htmlFile, buildMapHtml(), StandardCharsets.UTF_8);

        String fileUrl = htmlFile.toUri().toString();
        System.out.println("[MapView] Loading: " + fileUrl);
        engine.load(fileUrl);
    }

    private void copyResource(String classpathPath, Path dest) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(classpathPath)) {
            if (is == null) throw new IOException("Not found on classpath: " + classpathPath);
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private List<Incident> getFilteredList() {
        String sel = typeFilterCombo.getValue();
        if (sel == null || sel.equals("All")) return allIncidents;
        List<Incident> out = new ArrayList<>();
        for (Incident i : allIncidents) {
            if (sel.equals(i.getType())) out.add(i);
        }
        return out;
    }

    private void renderMarkers(List<Incident> incidents) {
        markerCountLabel.setText(incidents.size() + " markers");
        statusLabel.setText(incidents.isEmpty()
                ? "No incidents to display."
                : incidents.size() + " incidents loaded.");

        if (!mapReady) {
            pendingRender = incidents;
        } else {
            Platform.runLater(() -> pushMarkers(incidents));
        }
    }

    private void pushMarkers(List<Incident> incidents) {
        StringBuilder js = new StringBuilder();
        js.append("clearMarkers();");
        js.append("clearLegend();");

        for (Incident inc : incidents) {
            double lat = inc.getLatitude();
            double lng = inc.getLongitude();
            if (lat == 0.0 && lng == 0.0) continue;

            String color = colorFor(inc.getType());
            String popup = escapeJs(
                    "<b>" + inc.getType() + "</b> &mdash; " + inc.getLocation()
                            + (inc.getDescription() != null && !inc.getDescription().isEmpty()
                            ? "<br><i>" + inc.getDescription() + "</i>" : "")
                            + "<br><b>Status:</b> " + inc.getStatus()
                            + "<br><b>Date:</b> "   + inc.getDateString()
            );
            String legendLabel = escapeJs(inc.getType() + " — " + inc.getLocation());

            js.append("addMarker(")
                    .append(lat).append(",")
                    .append(lng).append(",'")
                    .append(color).append("','")
                    .append(popup).append("');");

            js.append("addLegendRow('")
                    .append(color).append("','")
                    .append(legendLabel).append("');");
        }

        try {
            engine.executeScript(js.toString());
        } catch (Exception e) {
            System.err.println("[MapView] JS execution failed: " + e.getMessage());
        }
    }

    private String buildMapHtml() {
        StringBuilder h = new StringBuilder();

        h.append("<!DOCTYPE html><html><head><meta charset='utf-8'/>")
                .append("<link rel='stylesheet' href='leaflet.css'/>")
                .append("<style>")

                .append("* { margin:0; padding:0; box-sizing:border-box; }")
                .append("html, body { width:100%; height:100%; overflow:hidden;")
                .append("  font-family:'Segoe UI',Arial,sans-serif; }")

                // ── FIX 1: Do NOT override Leaflet's transforms.
                // The old "transform:none !important" block was the #1 cause of
                // broken zoom, misaligned tiles, and inability to zoom out.
                // Leaflet needs CSS transforms to position tiles correctly.

                .append("#map {")
                .append("  position:absolute; top:0; left:0; bottom:0; right:220px;")
                .append("  background:#e5e7eb;")
                .append("}")
                .append("#legend {")
                .append("  position:absolute; top:0; right:0; bottom:0; width:220px;")
                .append("  background:#ffffff; border-left:1px solid #e2e5ea;")
                .append("  display:flex; flex-direction:column; overflow:hidden;")
                .append("}")
                .append("#legend-head {")
                .append("  padding:14px 14px 10px; border-bottom:1px solid #e2e5ea;")
                .append("  background:#fafbfc;")
                .append("}")
                .append("#legend-head .title {")
                .append("  font-size:10px; font-weight:800; color:#6b7280;")
                .append("  text-transform:uppercase; letter-spacing:.7px;")
                .append("}")
                .append("#legend-head .count {")
                .append("  font-size:22px; font-weight:800; color:#0f1623; margin-top:4px;")
                .append("}")
                .append("#legend-body { flex:1; overflow-y:auto; padding:10px; }")
                .append(".legend-empty {")
                .append("  font-size:12px; color:#9ca3af; padding:10px 4px; line-height:1.5;")
                .append("}")
                .append(".leg-row {")
                .append("  display:flex; align-items:flex-start; gap:8px;")
                .append("  padding:8px 6px; border-radius:7px; margin-bottom:4px;")
                .append("  border:1px solid #f0f2f5; background:#fafbfc;")
                .append("}")
                .append(".leg-row:last-child { margin-bottom:0; }")
                .append(".leg-dot {")
                .append("  width:11px; height:11px; border-radius:50%; flex-shrink:0;")
                .append("  border:2px solid white; box-shadow:0 1px 4px rgba(0,0,0,.28);")
                .append("  margin-top:3px;")
                .append("}")
                .append(".leg-txt {")
                .append("  font-size:11.5px; font-weight:600; color:#1e293b;")
                .append("  line-height:1.45; word-break:break-word;")
                .append("}")
                .append(".type-key {")
                .append("  display:flex; flex-wrap:wrap; gap:5px;")
                .append("  padding:10px 14px; border-bottom:1px solid #e2e5ea;")
                .append("}")
                .append(".type-pill {")
                .append("  display:inline-flex; align-items:center; gap:5px;")
                .append("  font-size:10px; font-weight:700; padding:3px 7px;")
                .append("  border-radius:99px; background:#f3f4f6; color:#374151;")
                .append("}")
                .append(".type-pill .pip { width:8px; height:8px; border-radius:50%; }")

                .append("</style></head><body>")

                .append("<div id='map'></div>")
                .append("<div id='legend'>")
                .append("  <div id='legend-head'>")
                .append("    <div class='title'>Incidents</div>")
                .append("    <div class='count' id='leg-count'>0</div>")
                .append("  </div>")
                .append("  <div class='type-key'>")
                .append(typePill("#3b82f6", "Flood"))
                .append(typePill("#ef4444", "Fire"))
                .append(typePill("#f97316", "Earthquake"))
                .append(typePill("#8b5cf6", "Typhoon"))
                .append(typePill("#a16207", "Landslide"))
                .append(typePill("#6b7280", "Other"))
                .append("  </div>")
                .append("  <div id='legend-body'>")
                .append("    <div class='legend-empty' id='leg-empty'>No incidents to display.</div>")
                .append("  </div>")
                .append("</div>")

                .append("<script src='leaflet.js'></script>")
                .append("<script>")

                // ── FIX 2: Do NOT disable L.Browser 3D flags.
                // Setting them to false forces Leaflet into a legacy pixel-offset
                // mode that breaks tile alignment and prevents zoom-out.
                // Modern Leaflet (1.9+) needs CSS3 transforms for correct rendering.

                // ── FIX 3: Cleaner map init — no canvas renderer, let Leaflet
                // pick SVG (its default and most compatible choice in WebView).
                .append("var map = L.map('map', {")
                .append("  center:              [").append(CENTER_LAT).append(", ").append(CENTER_LNG).append("],")
                .append("  zoom:                ").append(ZOOM).append(",")
                .append("  zoomControl:         true,")
                .append("  scrollWheelZoom:     true,")
                .append("  doubleClickZoom:     true,")
                .append("  zoomSnap:            1,")    // whole-number zoom steps feel snappier
                .append("  zoomDelta:           1,")
                .append("  wheelDebounceTime:   40,")   // ms between wheel events — prevents over-firing
                .append("  wheelPxPerZoomLevel: 80,")   // px of scroll needed per zoom step
                .append("  fadeAnimation:       true,")
                .append("  zoomAnimation:       true,")
                .append("  markerZoomAnimation: true")
                .append("});")

                // ── FIX 4: Wheel normalizer kept but much gentler.
                // JavaFX forwards raw trackpad/mouse-wheel deltas that are often
                // enormous (300–1200 px). We cap them at ±100 px so one flick
                // moves ~1 zoom level instead of jumping 10+ levels.
                // We no longer block zoom-out — negative deltas pass through fine.
                .append("(function(){")
                .append("  var el = document.getElementById('map');")
                .append("  el.addEventListener('wheel', function(e) {")
                .append("    if (e._norm) return;")
                .append("    e.stopImmediatePropagation();")
                .append("    e.preventDefault();")
                .append("    var raw   = e.deltaY;")
                .append("    var delta = Math.sign(raw) * Math.min(Math.abs(raw), 100);")
                .append("    var syn = new WheelEvent('wheel', {")
                .append("      bubbles:true, cancelable:true,")
                .append("      clientX:e.clientX, clientY:e.clientY,")
                .append("      deltaX:0, deltaY:delta, deltaMode:0")
                .append("    });")
                .append("    syn._norm = true;")
                .append("    el.dispatchEvent(syn);")
                .append("  }, { capture:true, passive:false });")
                .append("})();")

                // Tile layer — fast settings, no transform overrides
                .append("L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {")
                .append("  attribution:       '&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a>',")
                .append("  maxZoom:           19,")
                .append("  minZoom:           2,")
                .append("  subdomains:        ['a','b','c'],")
                .append("  updateWhenIdle:    false,")   // stream tiles as the map moves
                .append("  updateWhenZooming: false,")   // wait until zoom settles
                .append("  keepBuffer:        4,")       // tile halo — balance between memory and speed
                .append("  crossOrigin:       true")
                .append("}).addTo(map);")

                // Single invalidate after the page settles — replaces the storm
                // of repeated fixMap() calls that caused jitter in the original.
                .append("window.addEventListener('load', function() {")
                .append("  setTimeout(function(){ map.invalidateSize(); }, 250);")
                .append("});")
                .append("window.addEventListener('resize', function(){")
                .append("  map.invalidateSize();")
                .append("});")

                // Marker layer
                .append("var markerLayer = L.layerGroup().addTo(map);")

                // Public API used from Java
                .append("function clearMarkers() { markerLayer.clearLayers(); }")

                .append("function addMarker(lat, lng, color, popupHtml) {")
                .append("  if (!lat || !lng) return;")
                .append("  var icon = L.divIcon({")
                .append("    className: '',")
                .append("    html: '<div style=\"width:14px;height:14px;border-radius:50%;")
                .append(           "background:'+color+';border:2px solid #fff;")
                .append(           "box-shadow:0 1px 6px rgba(0,0,0,.45);\"></div>',")
                .append("    iconSize: [14,14], iconAnchor: [7,7]")
                .append("  });")
                .append("  L.marker([lat,lng], { icon:icon, keyboard:false })")
                .append("   .bindPopup(popupHtml, { autoPan:true, closeButton:true })")
                .append("   .addTo(markerLayer);")
                .append("}")

                .append("var legCount = 0;")
                .append("function clearLegend() {")
                .append("  legCount = 0;")
                .append("  document.getElementById('leg-count').textContent = '0';")
                .append("  document.getElementById('legend-body').innerHTML =")
                .append("    '<div class=\"legend-empty\" id=\"leg-empty\">No incidents to display.</div>';")
                .append("}")
                .append("function addLegendRow(color, label) {")
                .append("  legCount++;")
                .append("  document.getElementById('leg-count').textContent = legCount;")
                .append("  var e = document.getElementById('leg-empty');")
                .append("  if (e) e.remove();")
                .append("  var row = document.createElement('div');")
                .append("  row.className = 'leg-row';")
                .append("  row.innerHTML = '<span class=\"leg-dot\" style=\"background:'+color+'\"></span>'")
                .append("                + '<span class=\"leg-txt\">'+label+'</span>';")
                .append("  document.getElementById('legend-body').appendChild(row);")
                .append("}")

                .append("</script></body></html>");

        return h.toString();
    }

    private String typePill(String color, String label) {
        return "<div class='type-pill'>"
                + "<span class='pip' style='background:" + color + "'></span>"
                + label + "</div>";
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