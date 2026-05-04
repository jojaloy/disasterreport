package com.example.disasterreport.util;

import com.example.disasterreport.model.Incident;
import com.example.disasterreport.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    // Singleton: only one instance
    private static DatabaseManager instance;
    private Connection connection;

    private static final String URL  = "jdbc:mysql://localhost:3306/disasterreport_db";
    private static final String USER = "root";
    private static final String PASS = ""; // change this to your MySQL password

    // Private constructor — no one can do new DatabaseManager()
    private DatabaseManager() {
        connect();
    }

    // Call this everywhere: DatabaseManager.getInstance()
    public static DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    // Opens the JDBC connection
    private void connect() {
        try {
            connection = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("Database connected successfully.");
        } catch (SQLException e) {
            System.out.println("Database connection failed.");
            e.printStackTrace();
        }
    }

    public boolean saveUser(User user) {
        String sql = "INSERT INTO users(username, password, role) VALUES(?, ?, ?)";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRole());

            int rows = ps.executeUpdate();
            return rows > 0; // ✅ success if at least 1 row inserted

        } catch (SQLException e) {
            e.printStackTrace();
            return false; // ❌ failed insert
        }
    }

    // SELECT user by username and password — returns null if not found
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

    // INSERT a new incident
    public void saveIncident(Incident inc) {
        String sql = "INSERT INTO incidents(type, location, description, date, status, severity, reportedBy) VALUES(?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, inc.getType());
            ps.setString(2, inc.getLocation());
            ps.setString(3, inc.getDescription());
            ps.setDate(4, Date.valueOf(inc.getDate()));
            ps.setString(5, inc.getStatus());
            ps.setString(6, inc.getSeverity());
            ps.setString(7, inc.getReportedBy());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // SELECT all incidents — returns a List
    public List<Incident> getIncidents() {
        List<Incident> list = new ArrayList<>();
        String sql = "SELECT * FROM incidents ORDER BY date DESC";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Incident(
                        rs.getInt("incidentID"),
                        rs.getString("type"),
                        rs.getString("location"),
                        rs.getString("description"),
                        rs.getDate("date").toLocalDate(),
                        rs.getString("status"),
                        rs.getString("severity"),
                        rs.getString("reportedBy")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    // UPDATE incident status by ID
    public void updateIncidentStatus(int id, String status) {
        String sql = "UPDATE incidents SET status = ? WHERE incidentID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}