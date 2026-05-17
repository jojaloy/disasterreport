package com.example.disasterreport.model;

public class Responder extends User {
    private String agency;

    public Responder(int userID, String username, String password, String agency) {
        super(userID, username, password);
        this.agency = agency;
    }

    public String getAgency() { return agency; }

    @Override public String getRoleName() { return "responder"; }
    @Override public boolean canUpdateIncidentStatus() { return true; }
    @Override public boolean canManageUsers() { return false; }
}