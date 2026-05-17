package com.example.disasterreport.model;

public class Admin extends User {

    public Admin(int userID, String username, String password) {
        super(userID, username, password);
    }

    @Override public String getRoleName() { return "admin"; }
    @Override public boolean canUpdateIncidentStatus() { return true; }
    @Override public boolean canManageUsers() { return true; }
}