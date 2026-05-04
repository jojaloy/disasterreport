package com.example.disasterreport.model;

import com.example.disasterreport.util.DatabaseManager;
import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private int userID;
    private String username;
    private transient String password; // transient = skipped during serialization
    private String role;

    public User(int userID, String username, String password, String role) {
        this.userID = userID;
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public boolean login(String username, String password) {
        User found = DatabaseManager.getInstance().validateUser(username, password);
        return found != null;
    }

    public boolean register() {
        return DatabaseManager.getInstance().saveUser(this);
    }

    // Getters
    public int getUserID()       { return userID; }
    public String getUsername()  { return username; }
    public String getPassword()  { return password; }
    public String getRole()      { return role; }

    // Setters
    public void setUserID(int id)       { this.userID = id; }
    public void setUsername(String u)   { this.username = u; }
    public void setPassword(String p)   { this.password = p; }
    public void setRole(String r)       { this.role = r; }
}