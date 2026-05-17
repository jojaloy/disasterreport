package com.example.disasterreport.util;

import com.example.disasterreport.model.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {

    private static DatabaseManager instance;
    private Connection connection;

    private static final String URL  = "jdbc:mysql://localhost:3306/disasterreport_db";
    private static final String USER = "root";
    private static final String PASS = "";

    private DatabaseManager() { connect(); }

    public static DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    private void connect() {
        try {
            connection = DriverManager.getConnection(URL, USER, PASS);
            System.out.println("Database connected successfully.");
        } catch (SQLException e) { System.out.println("Database connection failed."); }
    }

    // UPDATED: Now uses user.getRoleName() due to the abstract class change
    public boolean saveUser(User user) {
        String sql = "INSERT INTO users(username, password, role) VALUES(?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRoleName());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    // UPDATED: The Object Factory for login
    public User validateUser(String username, String password) {
        String query = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int id = rs.getInt("userID");
                String user = rs.getString("username");
                String pass = rs.getString("password");
                String role = rs.getString("role");

                return switch (role.toLowerCase()) {
                    case "admin" -> new Admin(id, user, pass);
                    case "responder" -> {
                        String agency = rs.getString("agency");
                        yield new Responder(id, user, pass, agency != null ? agency : "Unknown Agency");
                    }
                    default -> {
                        int trust = rs.getInt("trust_score");
                        yield new Reporter(id, user, pass, trust);
                    }
                };
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // UPDATED: The Object Factory for the Admin Dashboard Table
    public List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        String sql = "SELECT userID, username, role, agency, trust_score FROM users ORDER BY userID";

        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                int id = rs.getInt("userID");
                String user = rs.getString("username");
                String role = rs.getString("role");

                switch (role.toLowerCase()) {
                    case "admin" -> list.add(new Admin(id, user, ""));
                    case "responder" -> {
                        String agency = rs.getString("agency");
                        list.add(new Responder(id, user, "", agency != null ? agency : "Unknown Agency"));
                    }
                    default -> {
                        int trust = rs.getInt("trust_score");
                        list.add(new Reporter(id, user, "", trust));
                    }
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public boolean deleteUser(int userID) {
        String sql = "DELETE FROM users WHERE userID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userID); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public boolean updateUserAdmin(int userID, String newUsername, String newRole, String newPassword) {
        try {
            if (newPassword != null && !newPassword.trim().isEmpty()) {
                String sql = "UPDATE users SET username = ?, role = ?, password = ? WHERE userID = ?";
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, newUsername); ps.setString(2, newRole); ps.setString(3, newPassword); ps.setInt(4, userID);
                return ps.executeUpdate() > 0;
            } else {
                String sql = "UPDATE users SET username = ?, role = ? WHERE userID = ?";
                PreparedStatement ps = connection.prepareStatement(sql);
                ps.setString(1, newUsername); ps.setString(2, newRole); ps.setInt(3, userID);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) { return false; }
    }

    public void saveIncident(Incident inc) {
        String sql = "INSERT INTO incidents (type, location, description, date, status, severity, reportedBy, latitude, longitude, image_data) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, inc.getType()); ps.setString(2, inc.getLocation()); ps.setString(3, inc.getDescription());
            ps.setDate(4, Date.valueOf(inc.getDate())); ps.setString(5, inc.getStatus()); ps.setString(6, inc.getSeverity());
            ps.setString(7, inc.getReportedBy()); ps.setDouble(8, inc.getLatitude()); ps.setDouble(9, inc.getLongitude());
            ps.setString(10, inc.getImageData());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Database Error! Did you run 'ALTER TABLE incidents ADD COLUMN image_data LONGTEXT;' in MySQL?");
            e.printStackTrace();
        }
    }

    public List<Incident> getIncidents() {
        List<Incident> list = new ArrayList<>();
        String sql = "SELECT * FROM incidents ORDER BY date DESC";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                double lat = 0.0, lng = 0.0; String img = "";
                try { lat = rs.getDouble("latitude"); lng = rs.getDouble("longitude"); img = rs.getString("image_data"); } catch (SQLException ignored) { }
                list.add(new Incident(rs.getInt("incidentID"), rs.getString("type"), rs.getString("location"), rs.getString("description"),
                        rs.getDate("date").toLocalDate(), rs.getString("status"), rs.getString("severity"), rs.getString("reportedBy"), lat, lng, img));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public void updateIncidentStatus(int id, String status) {
        String sql = "UPDATE incidents SET status = ? WHERE incidentID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status); ps.setInt(2, id); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void updateIncident(Incident inc) {
        String sql = "UPDATE incidents SET type=?, location=?, description=?, date=?, status=?, severity=?, reportedBy=?, latitude=?, longitude=?, image_data=? WHERE incidentID=?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, inc.getType()); ps.setString(2, inc.getLocation()); ps.setString(3, inc.getDescription());
            ps.setDate(4, Date.valueOf(inc.getDate())); ps.setString(5, inc.getStatus()); ps.setString(6, inc.getSeverity());
            ps.setString(7, inc.getReportedBy()); ps.setDouble(8, inc.getLatitude()); ps.setDouble(9, inc.getLongitude());
            ps.setString(10, inc.getImageData()); ps.setInt(11, inc.getIncidentID());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public boolean addRequest(String username, String type, String details) {
        String sql = "INSERT INTO requests(username, type, details, status) VALUES(?, ?, ?, 'PENDING')";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username); ps.setString(2, type); ps.setString(3, details); return ps.executeUpdate() > 0;
        } catch (SQLException e) { return false; }
    }

    public List<Request> getPendingRequests() {
        List<Request> list = new ArrayList<>();
        String sql = "SELECT * FROM requests WHERE status = 'PENDING' ORDER BY requestID ASC";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(new Request(rs.getInt("requestID"), rs.getString("username"),
                        rs.getString("type"), rs.getString("details"), rs.getString("status")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return list;
    }

    public int getPendingRequestsCount() {
        String sql = "SELECT COUNT(*) FROM requests WHERE status = 'PENDING'";
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {}
        return 0;
    }

    public void updateRequestStatus(int requestID, String status) {
        String sql = "UPDATE requests SET status = ? WHERE requestID = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status); ps.setInt(2, requestID); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void updateUserPassword(String username, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newPassword); ps.setString(2, username); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void updateUserRoleByUsername(String username, String newRole) {
        String sql = "UPDATE users SET role = ? WHERE username = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newRole); ps.setString(2, username); ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }
}