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
        refreshDashboard();
    }

    // Public so IncidentListController can call it after a status update
    public void refreshDashboard() {
        List<Incident> all = DatabaseManager.getInstance().getIncidents();

        long active   = all.stream().filter(i -> "Active".equals(i.getStatus())).count();
        long resolved = all.stream().filter(i -> "Resolved".equals(i.getStatus())).count();

        totalIncidentsLabel.setText(String.valueOf(all.size()));
        activeIncidentsLabel.setText(String.valueOf(active));
        resolvedIncidentsLabel.setText(String.valueOf(resolved));

        // Show last 5 incidents in the dashboard content area
        contentArea.getChildren().clear();

        if (all.isEmpty()) {
            contentArea.getChildren().add(
                    new Label("No incidents reported yet.")
            );
            return;
        }

        List<Incident> recent = all.stream()
                .skip(Math.max(0, all.size() - 5))
                .toList();

        for (Incident i : recent) {
            Label row = new Label(
                    "🔥 " + i.getType() + "  |  " + i.getLocation() + "  |  " + i.getStatus()
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

    // ── Sidebar navigation ────────────────────────────────────────────────
    @FXML private void showDashboard()        { refreshDashboard(); }
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

            // Shrink window back to login size
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

            contentArea.getChildren().setAll(view);

        } catch (Exception e) {
            e.printStackTrace();
            statusLabel.setText("Error loading view: " + fxmlFile);
        }
    }
}