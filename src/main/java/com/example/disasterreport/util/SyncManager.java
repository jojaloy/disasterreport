package com.example.disasterreport.util;

import com.example.disasterreport.model.Incident;
import java.net.Socket;
import java.sql.*;
import java.time.LocalDate;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SyncManager {
    private static final String LOCAL_DB = "jdbc:sqlite:offline_cache.db";
    private ScheduledExecutorService scheduler;

    public SyncManager() { createLocalTable(); }

    private void createLocalTable() {
        try (Connection conn = DriverManager.getConnection(LOCAL_DB);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS offline_incidents (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, location TEXT, " +
                    "description TEXT, date TEXT, status TEXT, severity TEXT, " +
                    "reportedBy TEXT, lat REAL, lng REAL, image_data TEXT)"); // Added image_data
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void queueLocally(Incident inc) {
        String sql = "INSERT INTO offline_incidents (type, location, description, date, status, severity, reportedBy, lat, lng, image_data) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(LOCAL_DB);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, inc.getType()); ps.setString(2, inc.getLocation()); ps.setString(3, inc.getDescription());
            ps.setString(4, inc.getDate().toString()); ps.setString(5, inc.getStatus()); ps.setString(6, inc.getSeverity());
            ps.setString(7, inc.getReportedBy()); ps.setDouble(8, inc.getLatitude()); ps.setDouble(9, inc.getLongitude());
            ps.setString(10, inc.getImageData());
            ps.executeUpdate();
            System.out.println("No connection. Incident queued offline.");
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void startAutoSync() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(this::syncPendingData, 5, 15, TimeUnit.SECONDS);
    }

    private void syncPendingData() {
        if (!isInternetAvailable()) return;

        try (Connection conn = DriverManager.getConnection(LOCAL_DB);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM offline_incidents")) {

            while (rs.next()) {
                Incident inc = new Incident(0, rs.getString("type"), rs.getString("location"),
                        rs.getString("description"), LocalDate.parse(rs.getString("date")),
                        rs.getString("status"), rs.getString("severity"), rs.getString("reportedBy"),
                        rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("image_data"));

                DatabaseManager.getInstance().saveIncident(inc);
                conn.createStatement().execute("DELETE FROM offline_incidents WHERE id = " + rs.getInt("id"));
                System.out.println("Offline report synced successfully!");
            }
        } catch (Exception e) {}
    }

    private boolean isInternetAvailable() {
        try (Socket socket = new Socket("8.8.8.8", 53)) { return true; } catch (Exception e) { return false; }
    }
}