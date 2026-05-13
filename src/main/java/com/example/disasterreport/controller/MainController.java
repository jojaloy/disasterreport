package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainController {

    @FXML private VBox  contentArea;
    @FXML private Label loggedInUserLabel, totalIncidentsLabel, activeIncidentsLabel, resolvedIncidentsLabel, statusLabel;
    @FXML private Button btnDashboard, btnReportIncident, btnMapView, btnManageUsers, btnRequestRole, btnAdminRequests;

    private User currentUser;

    private static final String NAV_ACTIVE = "-fx-background-color: #e8550a; -fx-text-fill: white; -fx-alignment: CENTER_LEFT; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 10 14 10 14; -fx-font-family: 'Segoe UI', 'Arial', sans-serif;";
    private static final String NAV_INACTIVE = "-fx-background-color: transparent; -fx-text-fill: #b0c4d8; -fx-alignment: CENTER_LEFT; -fx-font-size: 13px; -fx-font-weight: normal; -fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 10 14 10 14; -fx-font-family: 'Segoe UI', 'Arial', sans-serif;";

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loggedInUserLabel.setText("Logged in as: " + user.getUsername());

        if ("admin".equals(user.getRole())) {
            btnManageUsers.setVisible(true);   btnManageUsers.setManaged(true);
            btnAdminRequests.setVisible(true); btnAdminRequests.setManaged(true);
            btnRequestRole.setVisible(false);  btnRequestRole.setManaged(false);
        } else {
            btnManageUsers.setVisible(false);   btnManageUsers.setManaged(false);
            btnAdminRequests.setVisible(false); btnAdminRequests.setManaged(false);
            btnRequestRole.setVisible(true);    btnRequestRole.setManaged(true);
        }
        refreshDashboard();
    }

    public void refreshDashboard() {
        List<Incident> all = DatabaseManager.getInstance().getIncidents();
        long active = all.stream().filter(i -> "Active".equals(i.getStatus())).count();
        long resolved = all.stream().filter(i -> "Resolved".equals(i.getStatus())).count();
        totalIncidentsLabel.setText(String.valueOf(all.size()));
        activeIncidentsLabel.setText(String.valueOf(active));
        resolvedIncidentsLabel.setText(String.valueOf(resolved));

        contentArea.getChildren().clear();
        contentArea.setPadding(new Insets(26, 28, 26, 28));

        Label title = new Label("Dashboard Overview");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f1623;");

        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e2e5ea; -fx-padding: 22; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 2);");
        VBox.setVgrow(card, Priority.ALWAYS);

        HBox header = new HBox(0);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getChildren().addAll(headerCell("#", 40), headerCell("Type", 100), headerCell("Location", 200), headerCell("Severity", 90), headerCell("Status", 90));
        card.getChildren().addAll(new Label("Recent Reports"), header);

        List<Incident> recent = all.stream().limit(10).toList();
        for (Incident inc : recent) {
            HBox row = new HBox(0);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-background-color: white; -fx-border-color: #f1f3f5; -fx-border-width: 0 0 1 0; -fx-padding: 10 0;");

            Label sev = new Label(inc.getSeverity() != null ? inc.getSeverity().toUpperCase() : "MEDIUM");
            sev.setMinWidth(80); sev.setMaxWidth(80); sev.setAlignment(Pos.CENTER); sev.setStyle(severityBadgeStyle(inc.getSeverity()));

            Label sts = new Label(inc.getStatus());
            sts.setMinWidth(80); sts.setMaxWidth(80); sts.setAlignment(Pos.CENTER); sts.setStyle(statusBadgeStyle(inc.getStatus()));

            row.getChildren().addAll(dataCell("#"+inc.getIncidentID(), 40), dataCell(inc.getType(), 100), dataCell(inc.getLocation(), 200), sev, sts);
            card.getChildren().add(row);
        }
        contentArea.getChildren().addAll(title, card);
    }

    private void setActiveNavButton(Button activeBtn) {
        Button[] buttons = {btnDashboard, btnReportIncident, btnMapView, btnManageUsers, btnAdminRequests, btnRequestRole};
        for (Button btn : buttons) if (btn != null) btn.setStyle(btn == activeBtn ? NAV_ACTIVE : NAV_INACTIVE);
    }

    @FXML private void showDashboard() { setActiveNavButton(btnDashboard); refreshDashboard(); }
    @FXML private void showReportForm() { setActiveNavButton(btnReportIncident); loadView("ReportIncident.fxml"); }
    @FXML private void showMapView() { setActiveNavButton(btnMapView); loadView("MapView.fxml"); }
    @FXML private void showManageUsers() { setActiveNavButton(btnManageUsers); loadView("Manageusersview.fxml"); }
    @FXML private void showAdminRequests() { setActiveNavButton(btnAdminRequests); loadView("AdminRequestsView.fxml"); }

    @FXML
    private void handleRequestRole() {
        ChoiceDialog<String> dialog = new ChoiceDialog<>("responder", "responder", "admin");
        dialog.setTitle("Request Role Change");
        dialog.setHeaderText("Request a role upgrade");
        dialog.setContentText("Select the role you are applying for:");

        dialog.showAndWait().ifPresent(role -> {
            DatabaseManager.getInstance().addRequest(currentUser.getUsername(), "ROLE_CHANGE", role);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Success");
            alert.setHeaderText(null);
            alert.setContentText("Your request to become a " + role + " has been sent to the admin.");
            alert.showAndWait();
        });
    }

    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/disasterreport/LoginView.fxml"));
            javafx.scene.Parent root = loader.load();
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setMinWidth(480); stage.setMinHeight(600);
            stage.setWidth(480); stage.setHeight(600);
            stage.centerOnScreen();
            stage.setScene(new Scene(root, 480, 600));
            stage.setTitle("Disaster Report System – Login");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadView(String fxml) {
        try {
            contentArea.getChildren().clear();
            contentArea.setPadding(Insets.EMPTY);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/disasterreport/" + fxml));
            Node view = loader.load();
            Object ctrl = loader.getController();
            if (ctrl instanceof MapViewController mc) {
                mc.setMainController(this);
                if (currentUser != null) mc.setCurrentUserRole(currentUser.getRole());
                mc.loadIncidents(DatabaseManager.getInstance().getIncidents());
            }
            if (ctrl instanceof ReportIncidentController rc && currentUser != null) {
                rc.setCurrentUsername(currentUser.getUsername());
                rc.setMainController(this);
            }
            VBox.setVgrow(view, Priority.ALWAYS);
            contentArea.getChildren().setAll(view);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private Label headerCell(String t, double w) { Label l = new Label(t); l.setMinWidth(w); l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #9ca3af;"); return l; }
    private Label dataCell(String t, double w) { Label l = new Label(t); l.setMinWidth(w); l.setStyle("-fx-font-size: 13px; -fx-text-fill: #374151;"); return l; }

    private String severityBadgeStyle(String s) {
        String bg, fg;
        switch (s != null ? s.toUpperCase() : "MEDIUM") {
            case "LOW" -> { bg = "#dbeafe"; fg = "#1d4ed8"; }
            case "MEDIUM" -> { bg = "#fef3c7"; fg = "#b45309"; }
            case "HIGH" -> { bg = "#ffedd5"; fg = "#c2410c"; }
            case "CRITICAL" -> { bg = "#fee2e2"; fg = "#b91c1c"; }
            default -> { bg = "#f3f4f6"; fg = "#4b5563"; }
        }
        return String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 4; -fx-padding: 4 10;", bg, fg);
    }

    private String statusBadgeStyle(String s) {
        String bg, fg;
        switch (s != null ? s : "Monitoring") {
            case "Active" -> { bg = "#ffe4e1"; fg = "#c0392b"; }
            case "Monitoring" -> { bg = "#fef9c3"; fg = "#92400e"; }
            case "Resolved" -> { bg = "#dcfce7"; fg = "#15803d"; }
            default -> { bg = "#f3f4f6"; fg = "#6b7280"; }
        }
        return String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-font-size: 11px; -fx-font-weight: bold; -fx-background-radius: 20; -fx-padding: 4 10;", bg, fg);
    }
}