package com.example.disasterreport.controller;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;
import com.example.disasterreport.util.DatabaseManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Base64;
import java.util.List;

public class MainController {

    @FXML private VBox  contentArea;
    @FXML private Label loggedInUserLabel, totalIncidentsLabel, activeIncidentsLabel, resolvedIncidentsLabel, statusLabel, adminLabel;
    @FXML private Button btnDashboard, btnReportIncident, btnMapView, btnManageUsers, btnRequestRole, btnProfile;

    private User currentUser;

    private static final String NAV_ACTIVE = "-fx-background-color: #e8550a; -fx-text-fill: white; -fx-alignment: CENTER_LEFT; -fx-font-size: 13px; -fx-font-weight: bold; -fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 10 14 10 14; -fx-font-family: 'Segoe UI', 'Arial', sans-serif;";
    private static final String NAV_INACTIVE = "-fx-background-color: transparent; -fx-text-fill: #b0c4d8; -fx-alignment: CENTER_LEFT; -fx-font-size: 13px; -fx-font-weight: normal; -fx-background-radius: 7; -fx-cursor: hand; -fx-padding: 10 14 10 14; -fx-font-family: 'Segoe UI', 'Arial', sans-serif;";

    public void setCurrentUser(User user) {
        this.currentUser = user;
        loggedInUserLabel.setText("Logged in as: " + user.getUsername());

        // --- NEW OOP LOGIC ---
        // Let the User object dictate its own permissions!
        if (user.canManageUsers()) {
            adminLabel.setVisible(true);
            adminLabel.setManaged(true);
            btnManageUsers.setVisible(true);
            btnManageUsers.setManaged(true);
        } else {
            adminLabel.setVisible(false);
            adminLabel.setManaged(false);
            btnManageUsers.setVisible(false);
            btnManageUsers.setManaged(false);
        }

        // Hide the standalone Request Role button since it's now inside the Profile Tab!
        if (btnRequestRole != null) {
            btnRequestRole.setVisible(false);
            btnRequestRole.setManaged(false);
        }

        refreshDashboard();
    }

    public void refreshSidebarStats() {
        List<Incident> all = DatabaseManager.getInstance().getIncidents();
        long active = all.stream().filter(i -> "Active".equals(i.getStatus())).count();
        long resolved = all.stream().filter(i -> "Resolved".equals(i.getStatus())).count();
        totalIncidentsLabel.setText(String.valueOf(all.size()));
        activeIncidentsLabel.setText(String.valueOf(active));
        resolvedIncidentsLabel.setText(String.valueOf(resolved));

        // OOP UPDATE: Use canManageUsers() instead of checking string roles
        if (currentUser != null && currentUser.canManageUsers()) {
            int reqCount = DatabaseManager.getInstance().getPendingRequestsCount();
            if (reqCount > 0) {
                btnManageUsers.setText("Manage Users (" + reqCount + ")");
            } else {
                btnManageUsers.setText("Manage Users");
            }
        }
    }

    public void refreshDashboard() {
        refreshSidebarStats();
        contentArea.getChildren().clear();
        contentArea.setPadding(new Insets(26, 28, 26, 28));

        Label title = new Label("Dashboard Overview");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0f1623; -fx-font-family: 'Segoe UI', 'Arial', sans-serif;");

        VBox card = new VBox(16);
        card.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #e2e5ea; -fx-padding: 22; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 8, 0, 0, 2);");
        VBox.setVgrow(card, Priority.ALWAYS);

        HBox topBar = new HBox(10);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label recentLabel = new Label("Recent Reports");
        recentLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #374151; -fx-font-family: 'Segoe UI', 'Arial', sans-serif;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        TextField searchField = new TextField();
        searchField.setPromptText("Search location or type...");
        searchField.setPrefWidth(220);
        searchField.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #d1d5db; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 12; -fx-font-size: 13px; -fx-font-family: 'Segoe UI', 'Arial', sans-serif;");

        topBar.getChildren().addAll(recentLabel, spacer, searchField);

        TableView<Incident> table = new TableView<>();
        VBox.setVgrow(table, Priority.ALWAYS);

        table.setRowFactory(tv -> {
            TableRow<Incident> row = new TableRow<>() {
                @Override
                protected void updateItem(Incident item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setStyle("-fx-background-color: transparent; -fx-border-width: 0;");
                    } else {
                        String tint = getRowTint(item.getType());
                        setStyle("-fx-background-color: " + tint + "; -fx-border-color: #f3f4f6; -fx-border-width: 0 0 1px 0;");
                    }
                }
            };
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    openMapWithIncident(row.getItem().getIncidentID());
                }
            });
            row.hoverProperty().addListener((obs, wasHovered, isNowHovered) -> {
                if (!row.isEmpty() && row.getItem() != null) {
                    if (isNowHovered) {
                        row.setStyle("-fx-background-color: #e5e7eb; -fx-border-color: #d1d5db; -fx-border-width: 0 0 1px 0; -fx-cursor: hand;");
                    } else {
                        String tint = getRowTint(row.getItem().getType());
                        row.setStyle("-fx-background-color: " + tint + "; -fx-border-color: #f3f4f6; -fx-border-width: 0 0 1px 0;");
                    }
                }
            });
            return row;
        });

        String css = """
            .table-view { -fx-background-color: transparent; -fx-border-width: 0; -fx-padding: 0; }
            .table-view .column-header-background { -fx-background-color: transparent; -fx-border-width: 0 0 1px 0; -fx-border-color: #e2e5ea; }
            .table-view .column-header { -fx-background-color: transparent; -fx-border-width: 0; }
            .table-view .column-header .label { -fx-text-fill: #9ca3af; -fx-font-weight: bold; -fx-font-size: 11px; }
            .table-row-cell { -fx-background-color: transparent; -fx-border-width: 0 0 1px 0; -fx-border-color: #f3f4f6; }
            .table-row-cell:empty { -fx-border-width: 0; }
            .table-cell { -fx-border-width: 0; -fx-padding: 12px 8px; -fx-font-size: 13px; -fx-text-fill: #374151; -fx-alignment: CENTER-LEFT; }
            """;
        String dataUri = "data:text/css;base64," + Base64.getEncoder().encodeToString(css.getBytes());
        table.getStylesheets().add(dataUri);

        TableColumn<Incident, Integer> idCol = new TableColumn<>("#");
        idCol.setCellValueFactory(new PropertyValueFactory<>("incidentID"));
        idCol.setPrefWidth(50);

        TableColumn<Incident, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
        typeCol.setPrefWidth(120);
        typeCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String type, boolean empty) {
                super.updateItem(type, empty);
                if (empty || type == null) { setText(null); setStyle(""); return; }
                setText(type);
                String color = switch(type){case "Flood"->"#3b82f6";case "Fire"->"#ef4444";case "Earthquake"->"#f97316";case "Typhoon"->"#8b5cf6";case "Landslide"->"#a16207";default->"#6b7280";};
                setStyle("-fx-font-weight: bold; -fx-text-fill: " + color + "; -fx-alignment: CENTER-LEFT;");
            }
        });

        TableColumn<Incident, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(new PropertyValueFactory<>("location"));
        locCol.setPrefWidth(240);

        TableColumn<Incident, String> repCol = new TableColumn<>("Reported By");
        repCol.setCellValueFactory(new PropertyValueFactory<>("reportedBy"));
        repCol.setPrefWidth(120);
        repCol.setStyle("-fx-text-fill: #6b7280;");

        TableColumn<Incident, String> sevCol = new TableColumn<>("Severity");
        sevCol.setCellValueFactory(new PropertyValueFactory<>("severity"));
        sevCol.setPrefWidth(110);
        sevCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String sev, boolean empty) {
                super.updateItem(sev, empty);
                if (empty || sev == null) { setGraphic(null); return; }
                Label badge = new Label(sev.toUpperCase());
                badge.setStyle(severityBadgeStyle(sev));
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setAlignment(Pos.CENTER);
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        TableColumn<Incident, String> statCol = new TableColumn<>("Status");
        statCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statCol.setPrefWidth(110);
        statCol.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String stat, boolean empty) {
                super.updateItem(stat, empty);
                if (empty || stat == null) { setGraphic(null); return; }
                Label badge = new Label(stat);
                badge.setStyle(statusBadgeStyle(stat));
                badge.setMaxWidth(Double.MAX_VALUE);
                badge.setAlignment(Pos.CENTER);
                setGraphic(badge);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }
        });

        table.getColumns().addAll(idCol, typeCol, locCol, repCol, sevCol, statCol);

        ObservableList<Incident> allData = FXCollections.observableArrayList(DatabaseManager.getInstance().getIncidents());
        FilteredList<Incident> filteredData = new FilteredList<>(allData, p -> true);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            filteredData.setPredicate(inc -> {
                if (newVal == null || newVal.isEmpty()) return true;
                String lower = newVal.toLowerCase();
                return inc.getLocation().toLowerCase().contains(lower)
                        || inc.getType().toLowerCase().contains(lower)
                        || inc.getStatus().toLowerCase().contains(lower)
                        || inc.getReportedBy().toLowerCase().contains(lower);
            });
        });

        table.setItems(filteredData);

        card.getChildren().addAll(topBar, table);
        VBox marginBox = new VBox(18);
        marginBox.getChildren().addAll(title, card);
        VBox.setVgrow(marginBox, Priority.ALWAYS);

        contentArea.getChildren().add(marginBox);
        statusLabel.setText("Ready · " + allData.size() + " incidents loaded");
    }

    private void setActiveNavButton(Button activeBtn) {
        Button[] buttons = {btnDashboard, btnReportIncident, btnMapView, btnManageUsers, btnProfile, btnRequestRole};
        for (Button btn : buttons) if (btn != null) btn.setStyle(btn == activeBtn ? NAV_ACTIVE : NAV_INACTIVE);
    }

    @FXML private void showDashboard() { setActiveNavButton(btnDashboard); refreshDashboard(); }
    @FXML private void showReportForm() { setActiveNavButton(btnReportIncident); loadView("ReportIncident.fxml"); }
    @FXML private void showMapView() { setActiveNavButton(btnMapView); loadView("MapView.fxml"); }
    @FXML private void showProfile() { setActiveNavButton(btnProfile); loadView("ProfileView.fxml"); }

    @FXML private void showManageUsers() { setActiveNavButton(btnManageUsers); loadView("Manageusersview.fxml"); }

    private void openMapWithIncident(int incidentId) {
        setActiveNavButton(btnMapView);
        try {
            contentArea.getChildren().clear();
            contentArea.setPadding(Insets.EMPTY);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/disasterreport/MapView.fxml"));
            Node view = loader.load();
            Object ctrl = loader.getController();

            if (ctrl instanceof MapViewController mc) {
                mc.setMainController(this);
                // OOP UPDATE: Passing the object, not the string!
                if (currentUser != null) mc.setCurrentUser(currentUser);
                mc.loadIncidents(DatabaseManager.getInstance().getIncidents());
                mc.viewSpecificIncident(incidentId);
            }

            VBox.setVgrow(view, Priority.ALWAYS);
            contentArea.getChildren().setAll(view);
        } catch (Exception e) { e.printStackTrace(); }
    }

    @FXML
    private void handleRequestRole() {
        // Left this safely intact in case your FXML still triggers it,
        // though the Profile View is the new home for this feature!
        java.util.Optional<String> result = com.example.disasterreport.util.ModernDialog.showChoice(
                "Request Role Change",
                "Select the role you are applying for:",
                new String[]{"responder", "admin"}
        );

        result.ifPresent(role -> {
            DatabaseManager.getInstance().addRequest(currentUser.getUsername(), "ROLE_CHANGE", role);
            com.example.disasterreport.util.ModernDialog.showMessage(
                    "Success",
                    "Your request to become a " + role + " has been sent to the admin.",
                    false
            );
        });
    }

    @FXML
    private void handleLogout() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/disasterreport/LoginView.fxml"));
            javafx.scene.Parent root = loader.load();
            Stage stage = (Stage) contentArea.getScene().getWindow();
            stage.setMaximized(true);
            stage.setMinWidth(480); stage.setMinHeight(600);
            stage.setWidth(480); stage.setHeight(600);
            stage.centerOnScreen();
            stage.setScene(new Scene(root, 480, 600));
            stage.setTitle("Disaster Report System – Login");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadView(String fxml) {
        try {
            contentArea.getChildren().clear();
            contentArea.setPadding(Insets.EMPTY);
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/disasterreport/" + fxml));
            Node view = loader.load();
            Object ctrl = loader.getController();

            // Pass references down
            if (ctrl instanceof MapViewController mc) {
                mc.setMainController(this);
                // OOP UPDATE: Passing the object, not the string!
                if (currentUser != null) mc.setCurrentUser(currentUser);
                mc.loadIncidents(DatabaseManager.getInstance().getIncidents());
            }
            if (ctrl instanceof ReportIncidentController rc && currentUser != null) {
                rc.setCurrentUsername(currentUser.getUsername());
                rc.setMainController(this);
            }
            if (ctrl instanceof ProfileController pc) {
                // OOP UPDATE: Updated to match your new ProfileController method
                pc.setUserInfo(currentUser);
            }
            if (ctrl instanceof ManageUsersController uc) {
                uc.setMainController(this);
            }

            VBox.setVgrow(view, Priority.ALWAYS);
            contentArea.getChildren().setAll(view);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String getRowTint(String type) {
        if (type == null) return "transparent";
        return switch(type) {
            case "Flood" -> "#eff6ff";
            case "Fire" -> "#fef2f2";
            case "Earthquake" -> "#fff7ed";
            case "Typhoon" -> "#f5f3ff";
            case "Landslide" -> "#fefce8";
            default -> "transparent";
        };
    }

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