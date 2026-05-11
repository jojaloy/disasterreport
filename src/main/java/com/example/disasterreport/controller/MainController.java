package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
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

        contentArea.getChildren().clear();

        if (all.isEmpty()) {
            Label empty = new Label("No incidents reported yet.");
            empty.setStyle("-fx-font-size: 13px; -fx-text-fill: #9ca3af;" +
                    "-fx-font-family: 'Segoe UI', 'Arial', sans-serif;");
            contentArea.getChildren().add(empty);

            statusLabel.setText("Ready · 0 incidents loaded");
            return;
        }

        // ── Column header row ──────────────────────────────────────────────
        HBox header = new HBox(0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 0 0 6 0;");

        header.getChildren().addAll(
                headerCell("#",        52),
                headerCell("Type",     100),
                headerCell("Location", 200),
                headerCell("Date",     110),
                headerCell("Status",   100)
        );
        contentArea.getChildren().add(header);

        // ── Last 5 incidents ───────────────────────────────────────────────
        List<Incident> recent = all.stream()
                .skip(Math.max(0, all.size() - 5))
                .toList();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d, yyyy");

        for (Incident inc : recent) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setMaxWidth(Double.MAX_VALUE);
            row.setStyle("""
                -fx-background-color: white;
                -fx-border-color: #e2e5ea;
                -fx-border-radius: 6;
                -fx-background-radius: 6;
                -fx-border-width: 1;
                -fx-padding: 10 14 10 14;
            """);

            // ID
            Label idLbl = dataCell("#" + inc.getIncidentID(), 52);

            // Type — bold
            Label typeLbl = dataCell(inc.getType(), 100);
            typeLbl.setStyle(typeLbl.getStyle() + "-fx-font-weight: bold;");

            // Location
            Label locLbl = dataCell(inc.getLocation(), 200);

            // Date
            String dateStr = inc.getDate() != null ? inc.getDate().format(fmt) : "";
            Label dateLbl = dataCell(dateStr, 110);

            // Status badge pill
            Label statusBadge = new Label(inc.getStatus());
            statusBadge.setMinWidth(90);
            statusBadge.setMaxWidth(90);
            statusBadge.setAlignment(Pos.CENTER);
            statusBadge.setStyle(statusBadgeStyle(inc.getStatus()));

            row.getChildren().addAll(idLbl, typeLbl, locLbl, dateLbl, statusBadge);
            contentArea.getChildren().add(row);
        }

        statusLabel.setText("Ready · " + all.size() + " incidents loaded");
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

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Header cell with fixed min/max width */
    private Label headerCell(String text, double width) {
        Label lbl = new Label(text);
        lbl.setMinWidth(width);
        lbl.setMaxWidth(width);
        lbl.setStyle("""
            -fx-font-size: 11px;
            -fx-font-weight: bold;
            -fx-text-fill: #9ca3af;
            -fx-font-family: 'Segoe UI', 'Arial', sans-serif;
        """);
        return lbl;
    }

    /** Data cell with fixed min/max width */
    private Label dataCell(String text, double width) {
        Label lbl = new Label(text);
        lbl.setMinWidth(width);
        lbl.setMaxWidth(width);
        lbl.setStyle("""
            -fx-font-size: 13px;
            -fx-text-fill: #374151;
            -fx-font-family: 'Segoe UI', 'Arial', sans-serif;
        """);
        return lbl;
    }

    /**
     * Returns the pill badge style for a given status, matching screenshot:
     *  Active     → red-tinted pill
     *  Monitoring → yellow-tinted pill
     *  Resolved   → green-tinted pill
     */
    private String statusBadgeStyle(String status) {
        String bg, fg;
        switch (status) {
            case "Active"     -> { bg = "#ffe4e1"; fg = "#c0392b"; }
            case "Monitoring" -> { bg = "#fef9c3"; fg = "#92400e"; }
            case "Resolved"   -> { bg = "#dcfce7"; fg = "#15803d"; }
            default           -> { bg = "#f3f4f6"; fg = "#6b7280"; }
        }
        return String.format("""
            -fx-background-color: %s;
            -fx-text-fill: %s;
            -fx-font-size: 11px;
            -fx-font-weight: bold;
            -fx-background-radius: 20;
            -fx-padding: 4 12 4 12;
            -fx-font-family: 'Segoe UI', 'Arial', sans-serif;
        """, bg, fg);
    }
}