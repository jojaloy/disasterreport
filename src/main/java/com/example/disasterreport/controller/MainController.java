package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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
    @FXML private Label roleLabel;

    // Sidebar buttons — controlled by role
    @FXML private Button btnDashboard;
    @FXML private Button btnReportIncident;
    @FXML private Button btnIncidentList;
    @FXML private Button btnGenerateReport;
    @FXML private Button btnMapView;
    @FXML private Button btnManageUsers;  // admin only

    private User currentUser;

    // ── Called by LoginController after login ─────────────────────────────
    public void setCurrentUser(User user) {
        this.currentUser = user;
        loggedInUserLabel.setText(user.getUsername());
        applyRoleRestrictions(user.getRole());
        refreshDashboard();
    }

    /**
     * Role-based access control:
     *   admin     → full access to everything
     *   responder → can view list + map + update status, cannot manage users
     *   reporter  → can only report incidents + view dashboard
     */
    private void applyRoleRestrictions(String role) {
        String badge;
        String badgeColor;

        switch (role) {
            case "admin" -> {
                badge = "ADMIN";
                badgeColor = "#dc2626";
                // All buttons visible
                btnManageUsers.setVisible(true);
                btnManageUsers.setManaged(true);
                btnIncidentList.setDisable(false);
                btnGenerateReport.setDisable(false);
                btnMapView.setDisable(false);
            }
            case "responder" -> {
                badge = "RESPONDER";
                badgeColor = "#d97706";
                btnManageUsers.setVisible(false);
                btnManageUsers.setManaged(false);
                btnIncidentList.setDisable(false);
                btnGenerateReport.setDisable(true);
                btnMapView.setDisable(false);
                btnReportIncident.setDisable(false);
            }
            default -> { // reporter
                badge = "REPORTER";
                badgeColor = "#16a34a";
                btnManageUsers.setVisible(false);
                btnManageUsers.setManaged(false);
                btnIncidentList.setDisable(true);   // read-only list hidden
                btnGenerateReport.setDisable(true);
                btnMapView.setDisable(true);
            }
        }

        roleLabel.setText(badge);
        roleLabel.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: white;" +
                        "-fx-background-color: " + badgeColor + ";" +
                        "-fx-background-radius: 4; -fx-padding: 2 6 2 6;"
        );
    }

    // Public so IncidentListController can call it after a status update
    public void refreshDashboard() {
        List<Incident> all = DatabaseManager.getInstance().getIncidents();

        long active     = all.stream().filter(i -> "Active".equals(i.getStatus())).count();
        long resolved   = all.stream().filter(i -> "Resolved".equals(i.getStatus())).count();
        long monitoring = all.stream().filter(i -> "Monitoring".equals(i.getStatus())).count();

        totalIncidentsLabel.setText(String.valueOf(all.size()));
        activeIncidentsLabel.setText(String.valueOf(active));
        resolvedIncidentsLabel.setText(String.valueOf(resolved));

        contentArea.getChildren().clear();
        contentArea.setSpacing(8);

        if (all.isEmpty()) {
            Label empty = new Label("No incidents reported yet.");
            empty.setStyle("-fx-font-size: 13px; -fx-text-fill: #9ca3af;");
            contentArea.getChildren().add(empty);
            return;
        }

        // Show last 6 incidents
        List<Incident> recent = all.stream()
                .limit(6)
                .toList();

        for (Incident i : recent) {
            contentArea.getChildren().add(buildIncidentCard(i));
        }
    }

    /** Build a polished incident card for the dashboard */
    private Node buildIncidentCard(Incident i) {
        // Outer card
        javafx.scene.layout.HBox card = new javafx.scene.layout.HBox();
        card.setMaxWidth(Double.MAX_VALUE);
        card.setSpacing(0);
        card.setStyle(
                "-fx-background-color: white;" +
                        "-fx-border-color: #e5e7eb;" +
                        "-fx-border-radius: 8;" +
                        "-fx-background-radius: 8;" +
                        "-fx-border-width: 1;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.04), 4, 0, 0, 1);"
        );

        // Left accent bar (color by type)
        javafx.scene.layout.Region accent = new javafx.scene.layout.Region();
        accent.setMinWidth(5);
        accent.setMaxWidth(5);
        accent.setStyle("-fx-background-color: " + colorFor(i.getType()) + "; -fx-background-radius: 8 0 0 8;");

        // Content
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(4);
        content.setStyle("-fx-padding: 12 14 12 14;");
        javafx.scene.layout.HBox.setHgrow(content, javafx.scene.layout.Priority.ALWAYS);

        // Top row: type badge + location + date
        javafx.scene.layout.HBox topRow = new javafx.scene.layout.HBox(8);
        topRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label typeBadge = new Label(iconFor(i.getType()) + " " + i.getType());
        typeBadge.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: white;" +
                        "-fx-background-color: " + colorFor(i.getType()) + ";" +
                        "-fx-background-radius: 4; -fx-padding: 2 7 2 7;"
        );

        Label location = new Label("📍 " + i.getLocation());
        location.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1a2a4a;");
        javafx.scene.layout.HBox.setHgrow(location, javafx.scene.layout.Priority.ALWAYS);

        Label date = new Label(i.getDateString());
        date.setStyle("-fx-font-size: 11px; -fx-text-fill: #9ca3af;");

        topRow.getChildren().addAll(typeBadge, location, date);

        // Bottom row: description + status badge
        javafx.scene.layout.HBox bottomRow = new javafx.scene.layout.HBox(8);
        bottomRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        String desc = i.getDescription() != null && !i.getDescription().isEmpty()
                ? i.getDescription()
                : "No description provided.";
        if (desc.length() > 70) desc = desc.substring(0, 70) + "…";

        Label descLabel = new Label(desc);
        descLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");
        javafx.scene.layout.HBox.setHgrow(descLabel, javafx.scene.layout.Priority.ALWAYS);

        Label statusBadge = new Label(statusIcon(i.getStatus()) + " " + i.getStatus());
        statusBadge.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold;" +
                        "-fx-text-fill: " + statusTextColor(i.getStatus()) + ";" +
                        "-fx-background-color: " + statusBgColor(i.getStatus()) + ";" +
                        "-fx-background-radius: 4; -fx-padding: 2 7 2 7;"
        );

        bottomRow.getChildren().addAll(descLabel, statusBadge);
        content.getChildren().addAll(topRow, bottomRow);
        card.getChildren().addAll(accent, content);
        return card;
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

    private String statusIcon(String status) {
        if (status == null) return "●";
        return switch (status) {
            case "Active"     -> "🔴";
            case "Monitoring" -> "🟡";
            case "Resolved"   -> "🟢";
            default           -> "●";
        };
    }

    private String statusTextColor(String status) {
        if (status == null) return "#6b7280";
        return switch (status) {
            case "Active"     -> "#dc2626";
            case "Monitoring" -> "#d97706";
            case "Resolved"   -> "#16a34a";
            default           -> "#6b7280";
        };
    }

    private String statusBgColor(String status) {
        if (status == null) return "#f3f4f6";
        return switch (status) {
            case "Active"     -> "#fee2e2";
            case "Monitoring" -> "#fef3c7";
            case "Resolved"   -> "#dcfce7";
            default           -> "#f3f4f6";
        };
    }

    // ── Sidebar navigation ────────────────────────────────────────────────
    @FXML private void showDashboard()        { refreshDashboard(); }
    @FXML private void showReportForm()       { loadView("ReportIncident.fxml"); }
    @FXML private void showIncidentList()     { loadView("IncidentListView.fxml"); }
    @FXML private void showMapView()          { loadView("MapView.fxml"); }
    @FXML private void showReportGenerator()  { loadView("ReportIncident.fxml"); }
    @FXML private void showManageUsers()      { loadView("ManageUsersView.fxml"); }

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
                lc.setCurrentUserRole(currentUser != null ? currentUser.getRole() : "reporter");
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

    public User getCurrentUser() { return currentUser; }
}