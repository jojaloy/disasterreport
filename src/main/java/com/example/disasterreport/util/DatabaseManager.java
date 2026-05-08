package com.example.disasterreport.util;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static DatabaseManager instance;
    private Connection connection;

    private static final String URL  = "jdbc:mysql://localhost:3306/disasterreport_db";
    private static final String USER = "root";
    private static final String PASS = ""; // change to your MySQL password

    private DatabaseManager() { connect(); }

    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private void connect() {
        try {
            connection = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("Database connected successfully.");
        } catch (SQLException e) {
            System.out.println("Database connection failed: " + e.getMessage());
        }
    }

    // ── User operations ───────────────────────────────────────────────────

    public boolean saveUser(User user) {
        String sql = "INSERT INTO users(username, password, role) VALUES(?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRole());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public User validateUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(
                        rs.getInt("userID"),
                        rs.getString("username"),
                        rs.getString("password"),
                        rs.getString("role")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ── Incident operations ───────────────────────────────────────────────

    /**
     * INSERT a new incident (includes lat/lng if present).
     *
     * SQL schema expected:
     *   ALTER TABLE incidents
     *     ADD COLUMN latitude  DOUBLE NOT NULL DEFAULT 0,
     *     ADD COLUMN longitude DOUBLE NOT NULL DEFAULT 0;
     */
    public void saveIncident(Incident inc) {
        String sql = """
            INSERT INTO incidents
              (type, location, description, date, status, severity, reportedBy, latitude, longitude)
            VALUES (?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, inc.getType());
            ps.setString(2, inc.getLocation());
            ps.setString(3, inc.getDescription());
            ps.setDate  (4, Date.valueOf(inc.getDate()));
            ps.setString(5, inc.getStatus());
            ps.setString(6, inc.getSeverity());
            ps.setString(7, inc.getReportedBy());
            ps.setDouble(8, inc.getLatitude());
            ps.setDouble(9, inc.getLongitude());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** SELECT all incidents ordered by date descending. */
    public List<Incident> getIncidents() {
        List<Incident> list = new ArrayList<>();
        String sql = "SELECT * FROM incidents ORDER BY date DESC";
        try (Statement st  = connection.createStatement();
             ResultSet rs  = st.executeQuery(sql)) {

            while (rs.next()) {
                // Gracefully handle tables that don't yet have lat/lng columns
                double lat = 0.0, lng = 0.0;
                try {
                    lat = rs.getDouble("latitude");
                    lng = rs.getDouble("longitude");
                } catch (SQLException ignored) { /* columns not added yet */ }

                list.add(new Incident(
                        rs.getInt   ("incidentID"),
                        rs.getString("type"),
                        rs.getString("location"),
                        rs.getString("description"),
                        rs.getDate  ("date").toLocalDate(),
                        rs.getString("status"),
                        rs.getString("severity"),
                        rs.getString("reportedBy"),
                        lat, lng
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    /** UPDATE only the status field (used from IncidentListController). */
    public void updateIncidentStatus(int id, String status) {
        String sql = "UPDATE incidents SET status = ? WHERE incidentID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt   (2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Full UPDATE — updates every editable field.
     * Called by IncidentListController.handleUpdateStatus() after setting
     * the new status on the model object.
     */
    public void updateIncident(Incident inc) {
        String sql = """
            UPDATE incidents
               SET type        = ?,
                   location    = ?,
                   description = ?,
                   date        = ?,
                   status      = ?,
                   severity    = ?,
                   reportedBy  = ?,
                   latitude    = ?,
                   longitude   = ?
             WHERE incidentID  = ?
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, inc.getType());
            ps.setString(2, inc.getLocation());
            ps.setString(3, inc.getDescription());
            ps.setDate  (4, Date.valueOf(inc.getDate()));
            ps.setString(5, inc.getStatus());
            ps.setString(6, inc.getSeverity());
            ps.setString(7, inc.getReportedBy());
            ps.setDouble(8, inc.getLatitude());
            ps.setDouble(9, inc.getLongitude());
            ps.setInt   (10, inc.getIncidentID());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── NEW: Get all users (admin panel) ────────────────────────────────────

    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT userID, username, role FROM users ORDER BY userID";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new User(
                        rs.getInt("userID"),
                        rs.getString("username"),
                        "",   // never expose password in list
                        rs.getString("role")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

// ── NEW: Update a user's role (admin panel) ─────────────────────────────

    public boolean updateUserRole(int userID, String newRole) {
        String sql = "UPDATE users SET role = ? WHERE userID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newRole);
            ps.setInt(2, userID);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
}