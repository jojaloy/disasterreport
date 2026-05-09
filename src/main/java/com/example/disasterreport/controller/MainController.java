package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ParallelTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.List;

public class MainController {

    @FXML private VBox  contentArea;
    @FXML private Label loggedInUserLabel;
    @FXML private Label totalIncidentsLabel;
    @FXML private Label activeIncidentsLabel;
    @FXML private Label resolvedIncidentsLabel;
    @FXML private Label statusLabel;

    private User currentUser;

    // ── Called by LoginController after login ─────────────────────────────
    public void setCurrentUser(User user) {
        this.currentUser = user;
        loggedInUserLabel.setText("Logged in as: " + user.getUsername());
        showDashboardContent();
    }

    /**
     * Refreshes ONLY the three counter labels.
     * Safe to call from any sub-controller at any time.
     */
    public void refreshDashboard() {
        List<Incident> all = DatabaseManager.getInstance().getIncidents();

        long active     = all.stream().filter(i -> "Active".equals(i.getStatus())).count();
        long monitoring = all.stream().filter(i -> "Monitoring".equals(i.getStatus())).count();
        long resolved   = all.stream().filter(i -> "Resolved".equals(i.getStatus())).count();

        totalIncidentsLabel.setText(String.valueOf(all.size()));
        activeIncidentsLabel.setText(String.valueOf(active + monitoring));
        resolvedIncidentsLabel.setText(String.valueOf(resolved));
    }

    /**
     * Refreshes counters AND rebuilds the incident rows in the content area.
     * Called only when the user explicitly views the dashboard.
     */
    private void showDashboardContent() {
        refreshDashboard();

        List<Incident> all = DatabaseManager.getInstance().getIncidents();
        contentArea.getChildren().clear();

        if (all.isEmpty()) {
            Label empty = new Label("No incidents reported yet.");
            empty.setStyle(
                    "-fx-font-size: 13px; -fx-text-fill: #475569; " +
                            "-fx-padding: 20 22 20 22; -fx-font-family: 'Segoe UI';"
            );
            contentArea.getChildren().add(empty);
            return;
        }

        // Newest 10 incidents first
        List<Incident> recent = all.stream()
                .sorted((a, b) -> {
                    if (a.getDate() == null && b.getDate() == null) return 0;
                    if (a.getDate() == null) return 1;
                    if (b.getDate() == null) return -1;
                    return b.getDate().compareTo(a.getDate());
                })
                .limit(10)
                .toList();

        for (int i = 0; i < recent.size(); i++) {
            HBox row = buildIncidentRow(recent.get(i), i % 2 == 0);
            contentArea.getChildren().add(row);
            animateRowIn(row, i);
        }
    }

    /**
     * Builds one colour-coded incident row:
     *   [type badge] [location] [status badge] [date] [reported by]
     */
    private HBox buildIncidentRow(Incident inc, boolean alternate) {
        HBox row = new HBox();
        row.setSpacing(0);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setMinHeight(50);
        row.setMaxWidth(Double.MAX_VALUE);

        String rowBg = alternate ? "#1a2f45" : "#16283a";
        String rowStyle = "-fx-background-color: " + rowBg + ";" +
                "-fx-padding: 10 22 10 22;" +
                "-fx-border-color: #1e3a5f;" +
                "-fx-border-width: 0 0 1 0;";
        row.setStyle(rowStyle);

        // Type badge
        String[] tc = typeColors(inc.getType());
        Label typeBadge = new Label(typeEmoji(inc.getType()) + "  " + nvl(inc.getType(), "Unknown"));
        typeBadge.setMinWidth(140);
        typeBadge.setMaxWidth(140);
        typeBadge.setStyle(
                "-fx-background-color: " + tc[0] + ";" +
                        "-fx-text-fill: " + tc[1] + ";" +
                        "-fx-background-radius: 6;" +
                        "-fx-padding: 5 10 5 10;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-family: 'Segoe UI';"
        );

        // Location
        Label locLabel = new Label(nvl(inc.getLocation(), "Unknown location"));
        locLabel.setMinWidth(200);
        locLabel.setMaxWidth(200);
        locLabel.setStyle(
                "-fx-font-size: 13px;" +
                        "-fx-text-fill: #cbd5e1;" +
                        "-fx-font-family: 'Segoe UI';"
        );

        // Status badge
        String[] sc = statusColors(inc.getStatus());
        Label statusBadge = new Label(nvl(inc.getStatus(), "Unknown"));
        statusBadge.setMinWidth(120);
        statusBadge.setMaxWidth(120);
        statusBadge.setStyle(
                "-fx-background-color: " + sc[0] + ";" +
                        "-fx-text-fill: " + sc[1] + ";" +
                        "-fx-background-radius: 999;" +
                        "-fx-padding: 4 12 4 12;" +
                        "-fx-font-size: 11px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-font-family: 'Segoe UI';"
        );

        // Date
        Label dateLabel = new Label(nvl(inc.getDateString(), "—"));
        dateLabel.setMinWidth(110);
        dateLabel.setMaxWidth(110);
        dateLabel.setStyle(
                "-fx-font-size: 12px;" +
                        "-fx-text-fill: #64748b;" +
                        "-fx-font-family: 'Segoe UI';"
        );

        // Reported by
        Label reportedLabel = new Label(nvl(inc.getReportedBy(), "—"));
        reportedLabel.setStyle(
                "-fx-font-size: 12px;" +
                        "-fx-text-fill: #475569;" +
                        "-fx-font-family: 'Segoe UI';"
        );

        row.getChildren().addAll(typeBadge, locLabel, statusBadge, dateLabel, reportedLabel);

        // Hover highlight
        row.setOnMouseEntered(e ->
                row.setStyle(
                        "-fx-background-color: #243d57;" +
                                "-fx-padding: 10 22 10 22;" +
                                "-fx-border-color: #2563eb;" +
                                "-fx-border-width: 0 0 1 0;" +
                                "-fx-cursor: hand;"
                )
        );
        row.setOnMouseExited(e -> row.setStyle(rowStyle));

        return row;
    }

    // ── Staggered slide-in + fade for each row ────────────────────────────
    private void animateRowIn(Node node, int index) {
        node.setOpacity(0);
        node.setTranslateX(-20);

        FadeTransition fade = new FadeTransition(Duration.millis(300), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(300), node);
        slide.setFromX(-20);
        slide.setToX(0);

        ParallelTransition anim = new ParallelTransition(fade, slide);
        anim.setDelay(Duration.millis(45L * index));  // stagger rows by 45 ms each
        anim.play();
    }

    // ── Colour tables ─────────────────────────────────────────────────────

    /** [background, foreground] for disaster type badges */
    private String[] typeColors(String type) {
        if (type == null) return new String[]{"#1e293b", "#94a3b8"};
        return switch (type) {
            case "Flood"      -> new String[]{"#172554", "#93c5fd"};
            case "Fire"       -> new String[]{"#450a0a", "#fca5a5"};
            case "Earthquake" -> new String[]{"#431407", "#fdba74"};
            case "Typhoon"    -> new String[]{"#2e1065", "#c4b5fd"};
            case "Landslide"  -> new String[]{"#292524", "#d6b86a"};
            default           -> new String[]{"#1e293b", "#94a3b8"};
        };
    }

    /** [background, foreground] for status badges */
    private String[] statusColors(String status) {
        if (status == null) return new String[]{"#1e293b", "#94a3b8"};
        return switch (status) {
            case "Active"     -> new String[]{"#450a0a", "#fca5a5"};
            case "Monitoring" -> new String[]{"#422006", "#fcd34d"};
            case "Resolved"   -> new String[]{"#052e16", "#86efac"};
            default           -> new String[]{"#1e293b", "#94a3b8"};
        };
    }

    /** Emoji icon representing each disaster type */
    private String typeEmoji(String type) {
        if (type == null) return "⚠";
        return switch (type) {
            case "Flood"      -> "🌊";
            case "Fire"       -> "🔥";
            case "Earthquake" -> "🪨";
            case "Typhoon"    -> "🌀";
            case "Landslide"  -> "⛰";
            default           -> "⚠";
        };
    }

    /** Null-safe string helper */
    private String nvl(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    // ── Sidebar navigation ────────────────────────────────────────────────
    @FXML private void showDashboard()        { showDashboardContent(); }
    @FXML private void showReportForm()       { loadView("ReportIncident.fxml"); }
    @FXML private void showIncidentList()     { loadView("IncidentListView.fxml"); }
    @FXML private void showMapView()          { loadView("MapView.fxml"); }
    @FXML private void showReportGenerator()  { loadView("ReportIncident.fxml"); }

    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/disasterreport/LoginView.fxml")
            );
            javafx.scene.Parent root = loader.load();
            Stage stage = (Stage) contentArea.getScene().getWindow();

            stage.setMinWidth(480);
            stage.setMinHeight(600);
            stage.setWidth(480);
            stage.setHeight(600);
            stage.centerOnScreen();

            stage.setScene(new Scene(root, 480, 600));
            stage.setTitle("Disaster Report System – Login");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ── Internal loader ───────────────────────────────────────────────────
    private void loadView(String fxmlFile) {
        refreshDashboard();   // keep counters fresh on every navigation

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/disasterreport/" + fxmlFile)
            );
            Node view = loader.load();
            Object controller = loader.getController();

            if (controller instanceof IncidentListController lc) {
                lc.setMainController(this);
            }
            if (controller instanceof MapViewController mc) {
                mc.loadIncidents(DatabaseManager.getInstance().getIncidents());
            }
            if (controller instanceof ReportIncidentController rc && currentUser != null) {
                rc.setCurrentUsername(currentUser.getUsername());
            }

            // Fade the incoming view in
            view.setOpacity(0);
            contentArea.getChildren().setAll(view);

            FadeTransition ft = new FadeTransition(Duration.millis(240), view);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error loading view: " + fxmlFile);
        }
    }
}
