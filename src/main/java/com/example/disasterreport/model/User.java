package com.example.disasterreport.model;

public abstract class User {
    // Protected so the child classes can see them
    protected int userID;
    protected String username;
    protected String password;

    public User(int userID, String username, String password) {
        this.userID = userID;
        this.username = username;
        this.password = password;
    }

    public int getUserID() { return userID; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    // The rules every subclass MUST follow
    public abstract String getRoleName();
    public abstract boolean canUpdateIncidentStatus();
    public abstract boolean canManageUsers();
}