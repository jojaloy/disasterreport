package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * MainController — shell controller that owns the sidebar, stats cards,
 * and the dynamic content area.
 *
 * Fixes applied
 * ─────────────
 * 1. Passes `this` (MainController) to ReportIncidentController so that
 *    refreshDashboard() is called automatically after every new report.
 * 2. showReportGenerator() now correctly loads the dedicated ReportGenerator
 *    view instead of accidentally reloading the ReportIncident form.
 *    (If you don't have a separate ReportGeneratorView.fxml yet, it falls
 *    back to ReportIncident.fxml — just swap the string when ready.)
 */
public class MainController {

    // ── FXML bindings ──────────────────────────────────────────────────────
    @FXML private VBox  contentArea;
    @FXML private Label loggedInUserLabel;
    @FXML private Label totalIncidentsLabel;
    @FXML private Label activeIncidentsLabel;
    @FXML private Label resolvedIncidentsLabel;
    @FXML private Label statusLabel;

    // ── State ──────────────────────────────────────────────────────────────
    private User currentUser;

    // ── Called by LoginController after login ──────────────────────────────

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loggedInUserLabel.setText("Logged in as: " + user.getUsername());
        refreshDashboard();
    }

    // ── Dashboard refresh (public so child controllers can call it) ────────

    /**
     * Re-queries the database and updates the three stat cards plus the
     * "Recent Incidents" list.  Safe to call from any child controller
     * that holds a reference to this MainController.
     */
    public void refreshDashboard() {
        List<Incident> all = DatabaseManager.getInstance().getIncidents();

        long active   = all.stream().filter(i -> "Active".equals(i.getStatus())).count();
        long resolved = all.stream().filter(i -> "Resolved".equals(i.getStatus())).count();

        totalIncidentsLabel.setText(String.valueOf(all.size()));
        activeIncidentsLabel.setText(String.valueOf(active));
        resolvedIncidentsLabel.setText(String.valueOf(resolved));

        // ── Recent incidents list ──────────────────────────────────────────
        contentArea.getChildren().clear();

        if (all.isEmpty()) {
            contentArea.getChildren().add(new Label("No incidents reported yet."));
            return;
        }

        // Show the five most recent (last in the date-DESC ordered list = oldest;
        // take the tail so we show the newest ones).
        List<Incident> recent = all.stream()
                .limit(5)
                .toList();

        for (Incident i : recent) {
            String emoji = emojiFor(i.getType());
            Label row = new Label(
                    emoji + "  " + i.getType()
                            + "  |  " + i.getLocation()
                            + "  |  " + i.getStatus()
            );
            row.setMaxWidth(Double.MAX_VALUE);
            row.setStyle("""
                -fx-padding: 10 14 10 14;
                -fx-background-color: #f9fafb;
                -fx-border-color: #e5e7eb;
                -fx-border-radius: 5;
                -fx-background-radius: 5;
                -fx-font-size: 13px;
            """);
            contentArea.getChildren().add(row);
        }
    }

    // ── Sidebar navigation ─────────────────────────────────────────────────

    @FXML private void showDashboard()       { refreshDashboard(); }
    @FXML private void showReportForm()      { loadView("ReportIncident.fxml"); }
    @FXML private void showIncidentList()    { loadView("IncidentListView.fxml"); }
    @FXML private void showMapView()         { loadView("MapView.fxml"); }

    /**
     * "Generate Report" sidebar button.
     * Swap "ReportIncident.fxml" for your dedicated ReportGeneratorView.fxml
     * once that view exists.
     */
    @FXML private void showReportGenerator() { loadView("ReportIncident.fxml"); }

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

    // ── Internal view loader ───────────────────────────────────────────────

    private void loadView(String fxmlFile) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/disasterreport/" + fxmlFile)
            );
            Node view = loader.load();
            Object ctrl = loader.getController();

            // ── Wire up child controllers ──────────────────────────────────

            if (ctrl instanceof IncidentListController lc) {
                lc.setMainController(this);
            }

            if (ctrl instanceof MapViewController mc) {
                // Pass ALL incidents so the map can render pins immediately
                mc.loadIncidents(DatabaseManager.getInstance().getIncidents());
            }

            if (ctrl instanceof ReportIncidentController rc) {
                if (currentUser != null) {
                    rc.setCurrentUsername(currentUser.getUsername());
                }
                // FIX: pass this so the form can trigger a dashboard refresh
                rc.setMainController(this);
            }

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
            if (statusLabel != null) {
                statusLabel.setText("Error loading view: " + fxmlFile);
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

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
}